package io.choerodon.oauth.infra.common.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import io.choerodon.core.ldap.DirectoryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.AbstractContextMapper;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.query.SearchScope;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.ldap.LdapUtils;
import org.springframework.stereotype.Service;

import io.choerodon.oauth.api.service.LdapService;
import io.choerodon.oauth.api.service.OrganizationService;
import io.choerodon.oauth.api.service.UserService;
import io.choerodon.oauth.core.password.PasswordPolicyManager;
import io.choerodon.oauth.core.password.PasswordPolicyType;
import io.choerodon.oauth.core.password.domain.BasePasswordPolicyDO;
import io.choerodon.oauth.core.password.domain.BaseUserDO;
import io.choerodon.oauth.core.password.mapper.BasePasswordPolicyMapper;
import io.choerodon.oauth.core.password.record.PasswordRecord;
import io.choerodon.oauth.core.password.service.BaseUserService;
import io.choerodon.oauth.domain.entity.LdapE;
import io.choerodon.oauth.domain.entity.OrganizationE;
import io.choerodon.oauth.domain.entity.UserE;
import io.choerodon.oauth.infra.enums.LoginException;
import io.choerodon.oauth.infra.exception.CustomAuthenticationException;

import javax.naming.directory.DirContext;

import static org.springframework.ldap.query.LdapQueryBuilder.query;

/**
 * @author wuguokai
 */
@Service
public class ChoerodonAuthenticationProvider extends AbstractUserDetailsAuthenticationProvider {

    private static final String DATA_FORMAT = "MM月dd日 HH:mm";
    private static final Logger LOGGER = LoggerFactory.getLogger(ChoerodonAuthenticationProvider.class);
    @Value("${spring.application.name:oauth-server}")
    private String serviceName;
    @Autowired
    private CustomUserDetailsServiceImpl userDetailsService;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private BasePasswordPolicyMapper basePasswordPolicyMapper;
    @Autowired
    private BaseUserService baseUserService;
    @Autowired
    private PasswordRecord passwordRecord;
    @Autowired
    private PasswordPolicyManager passwordPolicyManager;
    @Autowired
    private LdapService ldapService;
    @Autowired
    private UserService userService;

    public void setUserDetailsService(CustomUserDetailsServiceImpl userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    public void setOrganizationService(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    public void setBasePasswordPolicyMapper(BasePasswordPolicyMapper basePasswordPolicyMapper) {
        this.basePasswordPolicyMapper = basePasswordPolicyMapper;
    }

    public void setBaseUserService(BaseUserService baseUserService) {
        this.baseUserService = baseUserService;
    }

    public void setPasswordRecord(PasswordRecord passwordRecord) {
        this.passwordRecord = passwordRecord;
    }

    public void setPasswordPolicyManager(PasswordPolicyManager passwordPolicyManager) {
        this.passwordPolicyManager = passwordPolicyManager;
    }

    public void setLdapService(LdapService ldapService) {
        this.ldapService = ldapService;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected UserDetails retrieveUser(
            String username,
            UsernamePasswordAuthenticationToken authentication) {
        //获取当前登录用户信息
        UserE user = userService.queryByLoginField(username);
        if (user == null) {
            throw new AuthenticationServiceException(LoginException.USERNAME_NOT_FOUND_OR_PASSWORD_IS_WRONG.value());
        }
        checkOrganization(user.getOrganizationId());

        BasePasswordPolicyDO passwordPolicy = new BasePasswordPolicyDO();
        passwordPolicy.setOrganizationId(user.getOrganizationId());
        passwordPolicy = basePasswordPolicyMapper.selectOne(passwordPolicy);
        //登录认证策略
        BaseUserDO baseUserDO = new BaseUserDO();
        BeanUtils.copyProperties(user, baseUserDO);
        Map returnMap = passwordPolicyManager.loginValidate("password", baseUserDO, passwordPolicy);
        Object lock = null;
        if (returnMap != null) {
            lock = returnMap.get(PasswordPolicyType.MAX_ERROR_TIME.getValue());
        }
        if (lock != null && !((Boolean) lock)
                && (user.getLocked() == null || (user.getLocked() != null && !user.getLocked()))) {
            //DONE 锁定用户
            Integer lockExpireTime = passwordPolicy.getLockedExpireTime();
            LOGGER.info("begin lock user, userId is: {} ", baseUserDO.getId());
            baseUserService.lockUser(baseUserDO.getId(), lockExpireTime);
            user = userService.queryByLoginField(username);
        }
        if (!user.getEnabled()) {
            throw new AuthenticationServiceException(LoginException.USER_IS_NOT_ACTIVATED.value());
        }
        //判断用户是否被锁
        long nowTime = System.currentTimeMillis();
        if (user.getLocked() != null && user.getLocked()) {
            Date lockDate = user.getLockedUntilAt();
            if (lockDate != null) {
                Long lockUntilTime = lockDate.getTime();
                if (lockUntilTime > nowTime) {
                    //账号处于被锁期间,返回登录页面
                    throw new CustomAuthenticationException(LoginException.ACCOUNT_IS_LOCKED.value(),
                            new SimpleDateFormat(DATA_FORMAT).format(lockDate));
                } else {
                    //给用户解锁
                    user.setLocked(false);
                    userService.updateSelective(user);
                    passwordRecord.unLockUser(user.getId());
                }
            }
        }
        return userDetailsService.loadUserByUsername(username);
    }

    @Override
    protected void additionalAuthenticationChecks(UserDetails userDetails,
                                                  UsernamePasswordAuthenticationToken authentication) {
        String rawPassword = new String(PasswordDecode.decode((String) authentication.getCredentials()));
        String username = userDetails.getUsername();
        String passWd = userDetails.getPassword();

        if (authentication.getDetails() instanceof CustomWebAuthenticationDetails) {
            CustomWebAuthenticationDetails details = (CustomWebAuthenticationDetails)
                    authentication.getDetails();
            String captchaCode = details.getCaptchaCode();
            String captcha = details.getCaptcha();
            UserE user = userService.queryByLoginField(username);
            BasePasswordPolicyDO passwordPolicy = new BasePasswordPolicyDO();
            passwordPolicy.setOrganizationId(user.getOrganizationId());
            passwordPolicy = basePasswordPolicyMapper.selectOne(passwordPolicy);
            BaseUserDO baseUserDO = new BaseUserDO();
            BeanUtils.copyProperties(user, baseUserDO);
            if (passwordPolicyManager.isNeedCaptcha(passwordPolicy, baseUserDO)) {
                if (captchaCode == null || captcha == null || "".equals(captcha)) {
                    throw new AuthenticationServiceException(LoginException.CAPTCHA_IS_NULL.value());
                } else if (!captchaCode.equalsIgnoreCase(captcha)) {
                    throw new AuthenticationServiceException(LoginException.CAPTCHA_IS_WRONG.value());
                }
            }
        }
        checkPassword(username, rawPassword, passWd);
    }

    private void checkPassword(String loginName, String credentials, String userPassword) {
        boolean passed;
        UserE user = userService.queryByLoginField(loginName);
        if (user.getLdap()) {
            passed = ldapAuthentication(user.getOrganizationId(), loginName, credentials);
        } else {
            BCryptPasswordEncoder encode = new BCryptPasswordEncoder();
            passed = encode.matches(credentials, userPassword);
        }
        if (passed) {
            return;
        }
        throw new AuthenticationServiceException(LoginException.USERNAME_NOT_FOUND_OR_PASSWORD_IS_WRONG.value());
    }

    private boolean ldapAuthentication(Long organizationId, String loginName, String credentials) {
        LdapE ldap = ldapService.queryByOrgId(organizationId);
        if (ldap != null && ldap.getEnabled()) {
            LdapContextSource contextSource = new LdapContextSource();
            String url = ldap.getServerAddress() + ":" + ldap.getPort();
            contextSource.setUrl(url);
            contextSource.setBase(ldap.getBaseDn());
            contextSource.afterPropertiesSet();
            LdapTemplate ldapTemplate = new LdapTemplate(contextSource);
            //ad目录不设置会报错
            if (DirectoryType.MICROSOFT_ACTIVE_DIRECTORY.value().equals(ldap.getDirectoryType())) {
                ldapTemplate.setIgnorePartialResultException(true);
            }
            String userDn = null;
            boolean anonymousFetchFailed = false;
            try {
                List<String> names =
                        ldapTemplate.search(
                                query()
                                        .searchScope(SearchScope.SUBTREE)
                                        .where("objectclass")
                                        .is(ldap.getObjectClass())
                                        .and(ldap.getLoginNameField())
                                        .is(loginName),
                                new AbstractContextMapper() {
                                    @Override
                                    protected Object doMapFromContext(DirContextOperations ctx) {
                                        return ctx.getNameInNamespace();
                                    }
                                });
                userDn = getUserDn(names, ldap.getLoginNameField(), loginName);
            } catch (Exception e) {
                anonymousFetchFailed = true;
                LOGGER.error("ldap anonymous search objectclass = {}, {} = {} failed, exception {}", ldap.getObjectClass(), ldap.getLoginNameField(), loginName, e);
            }
            if (anonymousFetchFailed) {
                userDn = accountAsUserDn2Authentication(loginName, ldap, contextSource, userDn);
            }
            return authentication(credentials, contextSource, userDn);
        } else {
            throw new AuthenticationServiceException(LoginException.LDAP_IS_DISABLE.value());
        }
    }

    private String accountAsUserDn2Authentication(String loginName, LdapE ldap, LdapContextSource contextSource, String userDn) {
        contextSource.setUserDn(ldap.getAccount());
        contextSource.setPassword(ldap.getPassword());
        contextSource.afterPropertiesSet();
        LdapTemplate template = new LdapTemplate(contextSource);
        if (DirectoryType.MICROSOFT_ACTIVE_DIRECTORY.value().equals(ldap.getDirectoryType())) {
            template.setIgnorePartialResultException(true);
        }
        try {
            List<String> names =
                    template.search(
                            query()
                                    .searchScope(SearchScope.SUBTREE)
                                    .where("objectclass")
                                    .is(ldap.getObjectClass())
                                    .and(ldap.getLoginNameField())
                                    .is(loginName),
                            new AbstractContextMapper() {
                                @Override
                                protected Object doMapFromContext(DirContextOperations ctx) {
                                    return ctx.getNameInNamespace();
                                }
                            });
            userDn = getUserDn(names, ldap.getLoginNameField(), loginName);
        } catch (Exception e) {
            LOGGER.error("use ldap account as userDn and password to authentication but search objectclass = {}, {} = {} failed, maybe the account or password is illegal, and check for the ldap config, exception {}", ldap.getObjectClass(), ldap.getLoginNameField(), loginName, e);
        }
        return userDn;
    }

    private String getUserDn(List<String> names, String loginFiled, String loginName) {
        if (names.isEmpty()) {
            LOGGER.warn("user not found");
        } else if (names.size() == 1) {
            return names.get(0);
        } else {
            LOGGER.warn("user {} = {} is not unique", loginFiled, loginName);
        }
        return null;
    }

    private boolean authentication(String credentials, LdapContextSource contextSource, String userDn) {
        if (userDn == null) {
            return false;
        } else {
            DirContext ctx = null;
            try {
                ctx = contextSource.getContext(userDn, credentials);
                return true;
            } catch (Exception e) {
                logger.error("Login failed, userDn or credentials may be wrong, exception {}", e);
                return false;
            } finally {
                // It is imperative that the created DirContext instance is always closed
                LdapUtils.closeContext(ctx);
            }

        }
    }


    private void checkOrganization(Long orgId) {
        OrganizationE organization = organizationService.queryOrganizationById(orgId);
        if (null == organization) {
            throw new AuthenticationServiceException(LoginException.ORGANIZATION_NOT_EXIST.value());
        }
        if (!organization.getEnabled()) {
            throw new AuthenticationServiceException(LoginException.ORGANIZATION_NOT_ENABLE.value());
        }
    }
}