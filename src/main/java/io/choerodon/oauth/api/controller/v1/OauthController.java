package io.choerodon.oauth.api.controller.v1;

import java.awt.image.BufferedImage;
import java.net.URL;
import java.security.Principal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.code.kaptcha.impl.DefaultKaptcha;

import io.choerodon.oauth.api.service.PrincipalService;
import io.choerodon.oauth.api.service.SystemSettingService;
import io.choerodon.oauth.infra.dataobject.SystemSettingDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.MessageSource;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import io.choerodon.oauth.api.service.UserService;
import io.choerodon.oauth.core.password.PasswordPolicyManager;
import io.choerodon.oauth.core.password.domain.BasePasswordPolicyDO;
import io.choerodon.oauth.core.password.domain.BaseUserDO;
import io.choerodon.oauth.core.password.mapper.BasePasswordPolicyMapper;
import io.choerodon.oauth.domain.entity.UserE;
import io.choerodon.oauth.infra.enums.LoginException;


/**
 * @author wuguokai
 */
@RefreshScope
@Controller
public class OauthController {
    private static final String LOGIN_FILED = "username";
    private static final String SPRING_SECURITY_LAST_EXCEPTION = "SPRING_SECURITY_LAST_EXCEPTION";
    private static final String SPRING_SECURITY_LAST_EXCEPTION_PARAMS = "SPRING_SECURITY_LAST_EXCEPTION_PARAMS";

    private static final String INDEX_DEFAULT = "index-default";
    private static final String INDEX_MOBILE = "index-mobile";
    private static final Logger LOGGER = LoggerFactory.getLogger(OauthController.class);

    @Value("${choerodon.oauth.loginPage.profile:default}")
    private String loginProfile;
    @Value("${choerodon.oauth.loginPage.title:Choerodon}")
    private String loginTitle;
    private Locale currentLocale = Locale.ROOT;

    private MessageSource messageSource;
    private DefaultKaptcha captchaProducer;
    private BasePasswordPolicyMapper basePasswordPolicyMapper;
    private PasswordPolicyManager passwordPolicyManager;
    private PrincipalService principalService;

    @Autowired
    private UserService userService;

    @Autowired
    private SystemSettingService systemSettingService;

    @Value("${choerodon.default.redirect.url:/}")
    private String defaultUrl;

    public OauthController(
            MessageSource messageSource,
            DefaultKaptcha captchaProducer,
            PasswordPolicyManager passwordPolicyManager,
            BasePasswordPolicyMapper basePasswordPolicyMapper,
            PrincipalService principalService) {
        this.messageSource = messageSource;
        this.captchaProducer = captchaProducer;
        this.passwordPolicyManager = passwordPolicyManager;
        this.basePasswordPolicyMapper = basePasswordPolicyMapper;
        this.principalService=principalService;
    }

    public void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public void setCaptchaProducer(DefaultKaptcha captchaProducer) {
        this.captchaProducer = captchaProducer;
    }

    public void setBasePasswordPolicyMapper(BasePasswordPolicyMapper basePasswordPolicyMapper) {
        this.basePasswordPolicyMapper = basePasswordPolicyMapper;
    }

    public void setPasswordPolicyManager(PasswordPolicyManager passwordPolicyManager) {
        this.passwordPolicyManager = passwordPolicyManager;
    }

    public void setLoginProfile(String loginProfile) {
        this.loginProfile = loginProfile;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    @GetMapping(value = "/")
    public String index() {
        return "redirect:" + defaultUrl;
    }

    @GetMapping(value = "/login")
    public String login(HttpServletRequest request, Model model, HttpSession session, @RequestParam(required = false) String device) {
        String returnPage = "mobile".equals(device) ? INDEX_MOBILE : INDEX_DEFAULT;
        SystemSettingDO systemSettingDO = systemSettingService.getSetting();

        if (systemSettingDO == null) {
            systemSettingDO = new SystemSettingDO();
        }

        Map<String, String> error = new HashMap<>();
        error.put(LoginException.USERNAME_NOT_FOUND_OR_PASSWORD_IS_WRONG.value(), null);

        model.addAttribute("systemName", systemSettingDO.getSystemName());

        if (systemSettingDO.getSystemLogo() != null)
            model.addAttribute("systemLogo", systemSettingDO.getSystemLogo().equals(new String("")) ? null : systemSettingDO.getSystemLogo()); // 为模版引擎统一数据
        model.addAttribute("systemTitle", systemSettingDO.getSystemTitle());
        if(systemSettingDO.getFavicon() != null)
            model.addAttribute("favicon", systemSettingDO.getFavicon().equals(new String("")) ? null : systemSettingDO.getFavicon()); // 为模版引擎统一数据

        if (!"default".equals(loginProfile)) {
            URL url = this.getClass().getResource("/templates/index-" + loginProfile + ".html");
            if (url != null) {
                model.addAllAttributes(error);
                returnPage = "index-" + loginProfile;
            }
        }

        String username = (String) session.getAttribute(LOGIN_FILED);
        String errorCode = (String) session.getAttribute(SPRING_SECURITY_LAST_EXCEPTION);
        Object[] params = (Object[]) session.getAttribute(SPRING_SECURITY_LAST_EXCEPTION_PARAMS);

        session.removeAttribute(SPRING_SECURITY_LAST_EXCEPTION);
        session.removeAttribute(LOGIN_FILED);
        session.removeAttribute(SPRING_SECURITY_LAST_EXCEPTION_PARAMS);

        if (username == null) {
            return returnPage;
        }
        UserE user = userService.queryByLoginField(username);
        //数据库中无该用户
        if (user == null) {
            error.put(LoginException.USERNAME_NOT_FOUND_OR_PASSWORD_IS_WRONG.value(),
                    messageSource.getMessage(LoginException.USERNAME_NOT_FOUND_OR_PASSWORD_IS_WRONG.value(),
                            null, currentLocale));

            model.addAllAttributes(error);
            return returnPage;
        }

        model.addAttribute("isNeedCaptcha", isNeedCaptcha(user));

        if (errorCode != null) {
            error.put(errorCode, messageSource.getMessage(errorCode, params, "登录失败", currentLocale));
        }
        model.addAllAttributes(error);
        return returnPage;
    }

    @RequestMapping(value = "/public/captcha")
    public void createCaptcha(HttpServletRequest request,
                              HttpServletResponse response) {
        response.setDateHeader("Expires", 0);
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.addHeader("Cache-Control", "post-check=0, pre-check=0");
        response.setHeader("Pragma", "no-cache");
        response.setContentType("image/jpeg");
        ServletOutputStream out = null;
        try {
            String capText = captchaProducer.createText();
            HttpSession session = request.getSession();
            session.setAttribute("captchaCode", capText);
            BufferedImage bi = captchaProducer.createImage(capText);
            out = response.getOutputStream();
            ImageIO.write(bi, "jpg", out);
            out.flush();
        } catch (Exception e) {
            LOGGER.info("create captcha fail: {}", e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception e) {
                    LOGGER.info("captcha output close fail: {}", e);
                }
            }
        }
    }

    @ResponseBody
    @RequestMapping("/api/user")
    public Principal user(Principal principal) {
        if(((OAuth2Authentication) principal).getPrincipal() instanceof String){
            return principalService.setClientDetailUserDetails(principal);
        }
        return principal;
    }

    private boolean isNeedCaptcha(UserE user) {
        BaseUserDO baseUserDO = new BaseUserDO();
        BeanUtils.copyProperties(user, baseUserDO);
        BasePasswordPolicyDO passwordPolicy = new BasePasswordPolicyDO();
        passwordPolicy.setOrganizationId(user.getOrganizationId());
        passwordPolicy = basePasswordPolicyMapper.selectOne(passwordPolicy);
        return !user.getLocked() && passwordPolicyManager.isNeedCaptcha(passwordPolicy, baseUserDO);
    }
}
