package com.example.esti.dto;

import com.example.esti.entity.MasterCode;
import com.example.esti.entity.MasterCodeType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MasterCodeResponse {

    private Long id;
    private MasterCodeType type;
    private String label;
    private Integer sortOrder;
    private Boolean active;

    public static MasterCodeResponse from(MasterCode m) {
        MasterCodeResponse res = new MasterCodeResponse();
        res.setId(m.getId());
        res.setType(m.getType());
        res.setLabel(m.getLabel());
        res.setSortOrder(m.getSortOrder());
        res.setActive(m.getActive());
        return res;
    }
}
