<template>
  <header class="navbar navbar-expand sticky-top bg-white border-bottom app-header">
    <nav class="container-fluid px-3">
      <router-link to="/" class="navbar-brand fw-bold">esti</router-link>
      <ul class="navbar-nav me-auto">
        <li class="nav-item">
          <router-link to="/upload" class="nav-link">카탈로그 관리</router-link>
        </li>
        <li class="nav-item">
          <router-link to="/proposal/list" class="nav-link">제안서 목록</router-link>
        </li>
      </ul>
      <router-link :to="{ name: 'proposal-new' }" class="btn btn-primary btn-sm">
        + 새 제안서
      </router-link>
    </nav>
  </header>
</template>

<style scoped>
.app-header {
  height: var(--esti-header-height);
}
.nav-link.router-link-active {
  color: var(--bs-primary);
  font-weight: 600;
}
</style>
