package io.choerodon.oauth.infra.feign.fallback;

import io.choerodon.core.exception.CommonException;
import io.choerodon.oauth.infra.dataobject.UserDO;
import io.choerodon.oauth.infra.feign.DevopsServiceClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * 版权：    上海云砺信息科技有限公司
 * 创建者:   youyifan
 * 创建时间: 2/14/2019 4:54 PM
 * 功能描述:
 * 修改历史:
 */
@Component
public class DevopsServiceClientFallback implements DevopsServiceClient {

    @Override
    public void updateUserPassword(Integer userId, String password) {
        throw new CommonException("error.GitlabUser.update.password");
    }
}
