package com.backbase.training.sec;

import com.backbase.portal.foundation.business.service.GroupBusinessService;
import com.backbase.portal.foundation.business.service.UserBusinessService;
import com.backbase.portal.foundation.commons.exceptions.*;
import com.backbase.portal.foundation.domain.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * A very simple example of AuthenticationProvider based on
 * a fixed user name with any password with more than 10 characters
 */
public class TrainingAuthenticationProvider implements AuthenticationProvider {

    private static final Logger LOG = LoggerFactory.getLogger(UserDetailsContextMapperImpl.class);
    private static final String USER = "zankar";
    private static final int MIN_LENGHT = 10;

    @Autowired
    private UserBusinessService userBusinessService;

    @Autowired
    private GroupBusinessService groupBusinessService;

    public GroupBusinessService getGroupBusinessService() {
        return groupBusinessService;
    }

    public void setGroupBusinessService(GroupBusinessService groupBusinessService) {
        LOG.debug("@setGroupBusinessService: " + groupBusinessService);
        this.groupBusinessService = groupBusinessService;
    }

    public UserBusinessService getUserBusinessService() {
        return userBusinessService;
    }

    public void setUserBusinessService(UserBusinessService userBusinessService) {
        LOG.debug("@setUserBusinessService: " + userBusinessService);
        this.userBusinessService = userBusinessService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        LOG.debug("@authenticate: " +
                    "authentication.name = " + authentication.getName() +
                    "authentication.credentials = " + authentication.getCredentials() +
                    "authentication.details = " + authentication.getDetails());

        UsernamePasswordAuthenticationToken userToken =
                (UsernamePasswordAuthenticationToken) authentication;

        String userName = userToken.getName();
        String password = userToken.getCredentials().toString();


        if (!USER.equals(userName) || password.length() < MIN_LENGHT) {
            throw new BadCredentialsException("Invalid username " + userName + " or password");
        }

        User user = updateUser(userName,password);

        UsernamePasswordAuthenticationToken result =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

        result.setDetails(userToken.getDetails());

        Collection<GrantedAuthority> auths = result.getAuthorities();

        LOG.debug("Authorities of " + userName);
        for(GrantedAuthority auth : auths) {
            LOG.debug("Authority: " + auth.getAuthority());
        }

        return result;
    }

    /**
     * Update the given user in CXP
     * @param userName
     * @param password
     * @return a User reference
     */
    private User updateUser(String userName, String password) {
        User user = null;
        try {
            user = userBusinessService.getUser(userName);
            LOG.debug("@updateUser: user " + user.getUsername());
        } catch (ItemNotFoundException e) {
            // Create new user
            LOG.debug("@updateUser: user " + userName + " not found ... creating a new one");
            user = new User();
            user.setUsername(userName);
            user.setPassword(password);
            user.setEnabled(true);
            try {
                user.getGroups().add(groupBusinessService.getGroup("admin"));
                LOG.debug("@updateUser: setting group. groups =  " + user.getGroups());
                LOG.debug("@updateUser: authorities =  " + user.getAuthorities());
                userBusinessService.createUser(user);
                LOG.debug("@updateUser: user created " + user);
            } catch (FoundationDataException e1) {
                throw new FoundationRuntimeException(e1);
            } catch (ItemNotFoundException e1) {
                throw new FoundationRuntimeException(e1);
            } catch (ItemAlreadyExistsException e1) {
                throw new FoundationRuntimeException(e1);
            }
        }
        return user;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        LOG.debug("@supports: " + authentication.getName());
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
