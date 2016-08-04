package com.backbase.training.sec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;

import com.backbase.portal.foundation.business.service.GroupBusinessService;
import com.backbase.portal.foundation.business.service.UserBusinessService;
import com.backbase.portal.foundation.commons.exceptions.FoundationDataException;
import com.backbase.portal.foundation.commons.exceptions.FoundationReadOnlyException;
import com.backbase.portal.foundation.commons.exceptions.FoundationRuntimeException;
import com.backbase.portal.foundation.commons.exceptions.ItemAlreadyExistsException;
import com.backbase.portal.foundation.commons.exceptions.ItemNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.ldap.userdetails.UserDetailsContextMapper;

import com.backbase.portal.foundation.domain.conceptual.UserPropertyDefinition;
import com.backbase.portal.foundation.domain.conceptual.StringPropertyValue;
import com.backbase.portal.foundation.domain.model.Group;
import com.backbase.portal.foundation.domain.model.Role;
import com.backbase.portal.foundation.domain.model.User;

/**
 * Operations to map a UserDetails object to and from a Spring LDAP DirContextOperations implementation.
 *
 * Used by LdapUserDetailsManager when loading and saving/creating user information, and also by the
 * LdapAuthenticationProvider to allow customization of the user data loaded during authentication.
 *
 * @see {@link UserDetailsContextMapper}
 *
 * @author BartH
 *
 */
public class UserDetailsContextMapperImpl implements UserDetailsContextMapper {

    private static final Logger log = LoggerFactory.getLogger(UserDetailsContextMapperImpl.class);

    private static final String LDAP_EMPLOYEES_GROUP = "ROLE_EMPLOYEES";
    private static final String PORTAL_EMPLOYEES_GROUP = "admin";  // it was "employees" (just a test)

    private UserBusinessService userBusinessService;
    private GroupBusinessService groupBusinessService;

    public UserDetailsContextMapperImpl(UserBusinessService userBusinessService, GroupBusinessService groupBusinessService) {
        this.userBusinessService = userBusinessService;
        this.groupBusinessService = groupBusinessService;
    }

    /**
     * Creates a fully populated UserDetails object for use by the security framework.
     *
     */
    public UserDetails mapUserFromContext(DirContextOperations ctx, String userName, Collection<? extends GrantedAuthority> authorities) {

        log.debug("entering mapUserFromContext");
        debugLdapAttributes(ctx);

        String password = null;
        String email = null;
        try {
            password = ctx.getAttributes().get("userPassword").get(0).toString();
            email = ctx.getAttributes().get("mail").get(0).toString();

            log.info("email={}", email);
        } catch (NamingException e) {
            log.error(e.getMessage(), e);
        }

        // Map the LDAP group(s) to Portal Groups
        List<Group> groups = mapLdapAuthoritiesToGroups(authorities);

        // Map the LDAP user to a Portal User
        User user = null;
        try {
            user = userBusinessService.getUser(userName);
            setEmail(user, email);
            user.getGroups().addAll(groups);
            try {
                userBusinessService.updateUser(userName, user);
            } catch (FoundationDataException e) {
                throw new FoundationRuntimeException(e);
            } catch (FoundationReadOnlyException e) {
                throw new FoundationRuntimeException(e);
            }
        } catch (ItemNotFoundException e) {
            log.debug("creating new user");
            // Create new user
            user = new User();
            user.setUsername(userName);
            user.setPassword(password);
            user.setEnabled(true);
            setEmail(user, email);
            user.getGroups().addAll(groups);
            try {
                userBusinessService.createUser(user);
            } catch (FoundationDataException e1) {
                throw new FoundationRuntimeException(e1);
            } catch (ItemNotFoundException e1) {
                throw new FoundationRuntimeException(e1);
            } catch (ItemAlreadyExistsException e1) {
                throw new FoundationRuntimeException(e1);
            }
            log.debug("user created");
        }

        debugUserAuthorities(user);

        log.debug("leaving mapUserFromContext");

        return user;
    }

    private void setEmail(User user, String email) {
        // Create property definition for the email field.
        UserPropertyDefinition emailProperty = new UserPropertyDefinition("email", new StringPropertyValue(email));
        // Add the property definitions to the user object
        // Don't override the existing properties
        user.getPropertyDefinitions().put("email", emailProperty);
    }


    /**
     * Reverse of the above operation. Populates a context object from the supplied user object.
     * Called when saving a user, for example.
     * @see {@link}
     */
    public void mapUserToContext(UserDetails user, DirContextAdapter ctx) {
        throw new IllegalStateException("Only retrieving data from LDAP is currently supported");
    }

    private List<Group> mapLdapAuthoritiesToGroups(Collection<? extends GrantedAuthority> authorities) {

        log.debug("entering mapLdapAuthoritiesToGroups");
        List<Group> groups = new ArrayList<Group>();

        if (authorities != null && !authorities.isEmpty()) {
            Iterator<? extends GrantedAuthority> iterator = authorities.iterator();
            log.info("Authorities:");
            while (iterator.hasNext()) {
                GrantedAuthority authority = iterator.next();
                String authorityName = authority.getAuthority();

                log.debug("loop pf authorities - name: " + authorityName);

                if (authorityName.equals(LDAP_EMPLOYEES_GROUP)) {
                    Group group = null;
                    try {
                        group = groupBusinessService.getGroup(PORTAL_EMPLOYEES_GROUP);
                        log.info("group={}", group);
                    } catch (ItemNotFoundException e) {

                        log.info("ItemNotFoundException! Create new group {}", PORTAL_EMPLOYEES_GROUP);

                        group = new Group();
                        group.setDescription("Group " + PORTAL_EMPLOYEES_GROUP);
                        group.setName(PORTAL_EMPLOYEES_GROUP);
                        group.setRole(Role.USER);
                        try {
                            groupBusinessService.createGroup(group);
                        } catch (ItemAlreadyExistsException e1) {
                            throw new FoundationRuntimeException(e1);
                        } catch (FoundationDataException e1) {
                            throw new FoundationRuntimeException(e1);
                        }
                    }
                    groups.add(group);
                    log.info("\tConverted "+ authority.toString() + " to group "+ group.getName());
                }
            }
        } else {
            log.info("No authorities found");
        }

        return groups;
    }

    private void debugLdapAttributes( final DirContextOperations ctx ) {

        NamingEnumeration<? extends Attribute> attributes = ctx.getAttributes().getAll();
        try {
            while (attributes.hasMore()) {
                Attribute attribute = attributes.next();
                log.info("attribute id={}, {}", attribute.getID(), attribute.toString());
            }
        } catch (NamingException e) {
            log.error(e.getMessage(), e);
        }

    }

    private void debugUserAuthorities(final User user) {

        Iterator<? extends GrantedAuthority> iterator = user.getAuthorities().iterator();
        log.info("Mapped User Authorities:");
        while (iterator.hasNext()) {
            GrantedAuthority authority = iterator.next();
            String authorityName = authority.getAuthority();
            log.info("\tauthorityName={}", authorityName);
        }

    }

}
