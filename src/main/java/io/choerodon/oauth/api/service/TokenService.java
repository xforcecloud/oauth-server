package io.choerodon.oauth.api.service;

public interface TokenService {
    /**
     * 根据tokenId 删除 db token /redis session / db refresh token
     *
     * @param tokenId tokenId
     */
    void deleteOne(String tokenId);

    /**
     * 根据登录名 删除 用户下的所有db token和redis session / db refresh token
     *
     * @param loginName 登录名
     */
    void deleteAllUnderUser(String loginName);
}
