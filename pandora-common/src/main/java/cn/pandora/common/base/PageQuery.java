package cn.pandora.common.base;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用分页查询参数
 */
@Data
public class PageQuery implements Serializable {

    @Min(value = 1, message = "页码最小为1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 500, message = "每页条数最大为500")
    private Integer pageSize = 10;

    private String orderBy;

    private Boolean asc = true;
}
