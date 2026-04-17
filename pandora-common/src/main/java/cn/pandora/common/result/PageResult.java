package cn.pandora.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果封装
 */
@Data
public class PageResult<T> implements Serializable {

    private long total;
    private long pageNum;
    private long pageSize;
    private long totalPages;
    private List<T> records;

    public static <T> PageResult<T> of(List<T> records, long total, long pageNum, long pageSize) {
        PageResult<T> page = new PageResult<>();
        page.setRecords(records);
        page.setTotal(total);
        page.setPageNum(pageNum);
        page.setPageSize(pageSize);
        page.setTotalPages((total + pageSize - 1) / pageSize);
        return page;
    }
}
