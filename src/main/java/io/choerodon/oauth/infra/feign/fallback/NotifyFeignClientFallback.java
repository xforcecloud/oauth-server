package io.choerodon.oauth.infra.feign.fallback;

import io.choerodon.core.notify.NoticeSendDTO;
import org.springframework.stereotype.Component;

import io.choerodon.core.exception.CommonException;
import io.choerodon.oauth.infra.feign.NotifyFeignClient;

/**
 * @author dongfan117@gmail.com
 */
@Component
public class NotifyFeignClientFallback implements NotifyFeignClient {
    private static final String FEIGN_ERROR = "notify.error";

    @Override
    public void postNotice(NoticeSendDTO dto) {
        throw new CommonException(FEIGN_ERROR);
    }

}
