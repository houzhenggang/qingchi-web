package com.qingchi.server.model;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * @author qinkaiyuan
 * @date 2019-10-28 16:11
 */
@Data
public class UserIdVO {
    @NotNull(message = "入参为空异常")
    private Integer id;
}
