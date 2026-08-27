import { ref } from 'vue'
import axios from 'axios'

// 마스터 선택지(평형·건물구분·적용부위·적용카테고리) 전역 싱글턴.
// Phase 6까지 labels.js 상수였던 자리라 호출부는 "그냥 있는 배열"처럼 쓴다 —
// 화면 진입마다 다시 받아오지 않도록 모듈 수준에서 한 번만 로드한다.

const apartmentTypes = ref([])
const buildingTypes = ref([])
const areas = ref([])
const categories = ref([])

let loaded = false
let inflight = null // 여러 컴포넌트가 동시에 mount돼도 요청은 1회

function apply(data) {
  apartmentTypes.value = data?.APARTMENT_TYPE ?? []
  buildingTypes.value = data?.BUILDING_TYPE ?? []
  areas.value = data?.AREA ?? []
  categories.value = data?.CATEGORY ?? []
}

async function load({ force = false } = {}) {
  if (loaded && !force) return
  if (inflight) return inflight

  inflight = axios
    .get('/api/master-codes/options')
    .then((res) => {
      apply(res.data)
      loaded = true
    })
    .catch((e) => {
      // 선택지를 못 받아도 화면 자체는 떠야 한다. 빈 목록이면 사용자가 설정 화면으로 갈 수 있다.
      console.error('마스터 선택지 조회 실패', e)
    })
    .finally(() => {
      inflight = null
    })

  return inflight
}

/** 설정 화면에서 값을 바꾼 뒤 호출 — 다음 load()가 서버에서 다시 받아오게 한다. */
function invalidate() {
  loaded = false
}

/*
 * 선택지 = 마스터의 현재 값 + 이미 저장돼 있는 값.
 * 마스터에서 숨긴 뒤에도 저장된 제안서는 그 값을 그대로 보여야 한다(M-6) —
 * 마스터만 그리면 고른 값이 화면에서 사라져 저장 시 조용히 빠진다.
 * saved는 문자열 하나 또는 배열 둘 다 받는다.
 */
export function withSaved(options, saved) {
  const kept = (Array.isArray(saved) ? saved : [saved]).filter((v) => v && !options.includes(v))
  return [...options, ...new Set(kept)]
}

export function useMasterCodes() {
  return { apartmentTypes, buildingTypes, areas, categories, load, invalidate }
}
