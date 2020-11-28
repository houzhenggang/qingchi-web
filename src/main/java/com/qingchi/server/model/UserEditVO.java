package com.qingchi.server.model;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * @author qinkaiyuan 查询结果可以没有set和空构造，前台传值可以没有get
 * @date 2019-08-13 23:34
 */
@Data
public class UserEditVO {
    private Integer id;
    @NotBlank(message = "入参为空异常")
    private String nickname;
    @NotBlank(message = "入参为空异常")
    private String gender;
    @NotBlank(message = "入参为空异常")
    private String birthday;
    private String location;
    /**
     * 微信账户，用户可以关联微信账户，方便添加好友
     */
    private String wxAccount;
    private String qqAccount;
    //微博
    private String wbAccount;
    private String contactAccount;
}
