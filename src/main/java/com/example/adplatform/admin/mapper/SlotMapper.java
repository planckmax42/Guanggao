package com.example.adplatform.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.common.enums.CommonStatus;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SlotMapper extends BaseMapper<SlotEntity> {

    @Select("SELECT slot_code FROM slot WHERE status = " + CommonStatus.ENABLED)
    List<String> selectEnabledSlotCodes();
}
