package io.choerodon.oauth.infra.feign;

import io.choerodon.oauth.infra.dataobject.UserDO;
import io.choerodon.oauth.infra.feign.fallback.GitlabServiceClientFallback;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 版权：    上海云砺信息科技有限公司
 * 创建者:   youyifan
 * 创建时间: 2/14/2019 4:53 PM
 * 功能描述:
 * 修改历史:
 */
@FeignClient(value = "gitlab-service", fallback = GitlabServiceClientFallback.class)
public interface GitlabServiceClient {
    @PutMapping("/v1/users/{userId}/password")
    ResponseEntity<UserDO> updateGitLabUserPassword(@PathVariable("userId") Integer userId,
                                                    @RequestParam(value = "password") String password);
}
