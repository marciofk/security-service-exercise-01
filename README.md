# Backbase Training Exercises

## Portal Backend - Module 3: LDAP Integration

In this tutorial you will configure portal to communicate with LDAP server embedded into Training Server application.

### Prerequisites

You need to get a training server up and running before performing this exercise.
Follow the instructions from [Training Server](https://github.com/Backbase/training-server).

### Installation & Configuration

- Clone the **security-service-exercise-01** into the **services** folder of your project.

- Include **security-service-exercise-01** module to the build.  Open `services/pom.xml` and add **security-ldap** in the `<modules>` section: 

	```xml
	    <modules>
	        ...	    
	        <module>security-service-exercise-01</module>
	        ...
	    </modules>
	```	
	Re-compile **services** by executing `mvn clean install` in the **services** folder.
	
- Enable newly created module in the Portalserver application. In the `<dependencies>` section of your portal server (`webapps/portalserver/pom.xml`), add the following dependency:

	```xml
	    <dependency>
	        <groupId>com.backbase.training</groupId>
	        <artifactId>security-service-exercise-01</artifactId>
	        <version>1.0-SNAPSHOT</version>
	    </dependency>
	```
	
*You can add this service as a dependency of your aggregator or BOM project, instead of directly change the pom.xml file of your portalserver project*

- Configure Portal security. Open **webapps/portalserver/src/main/resources/META-INF/spring/backbase-portal-business-security.xml**.

  > If the file can not be found, copy it from **webapps/portalserver/target/portalserver/WEB-INF/lib/security-portalserver-5.x.x.x.jar!/META-INF/spring/backbase-portal-business-security.xml**. If the **lib** folder does not exist, run the portalserver webapp using **mvn jetty:run-exploded**.

  Add the following elements in the configuration:
  
	```xml
	<!-- The LDAP context source -->
	<beans:bean id="ldapContextSource" class="org.springframework.security.ldap.DefaultSpringSecurityContextSource">
	    <beans:constructor-arg value=" ldap://localhost:33389/dc=backbase,dc=com "/>
	</beans:bean>

	<!--  The LDAP authentication provider configuration -->
	<beans:bean id="ldapAuthenticationProvider"
	            class="org.springframework.security.ldap.authentication.LdapAuthenticationProvider">
        
	    <beans:constructor-arg>
	        <beans:bean class="org.springframework.security.ldap.authentication.BindAuthenticator">
	            <beans:constructor-arg ref="ldapContextSource"/>
	            <beans:property name="userSearch">
	                <beans:bean class="org.springframework.security.ldap.search.FilterBasedLdapUserSearch">
	                    <beans:constructor-arg index="0" value="ou=people"/>
	                    <beans:constructor-arg index="1" value="(uid={0})"/>
	                    <beans:constructor-arg index="2" ref="ldapContextSource"/>
	                </beans:bean>
	            </beans:property>
	        </beans:bean>
	    </beans:constructor-arg>
        
	    <beans:constructor-arg>
	        <beans:bean class="org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator">
	            <beans:constructor-arg ref="ldapContextSource"/>
	            <beans:constructor-arg value="ou=groups"/>
	        </beans:bean>
	    </beans:constructor-arg>
        
	    <beans:property name="userDetailsContextMapper">
	        <beans:bean class="com.backbase.training.sec.UserDetailsContextMapperImpl">
	            <beans:constructor-arg ref="userService"/>
	            <beans:constructor-arg ref="groupService"/>
	        </beans:bean>
	    </beans:property>
        
	</beans:bean>
	```
	
	Include `ldapAuthenticationProvider` in the `<authentication-manager>` block:
	
	```xml
	<authentication-manager>
	    ...
	    <authentication-provider ref="ldapAuthenticationProvider"/>
	    ...
	</authentication-manager>
	```

### Build & Run

- If Portalserver application is already running, stop it by pressing *Ctrl+C*. Start Portalserver application by executing `mvn jetty:run` command from the **webapps/portalserver** directory.
- Make sure that the Training Server is running.
- Login with user *john/backbase* and make sure that user is logged in.

### Optional: Enabling a Custom Authentication Provider

This project comes with an example of Custom Authentication Provider: *com.backbase.training.sec.TrainingAuthenticationProvider*. It is a schematic example that only accepts a predefined user called *zankar* and the password must have at least 10 characters.

To enable this custom provider, you should define it as a bean in your *backbase-portal-business-security.xml* file. For example:

<beans:bean id="trainingProvider" class="com.backbase.training.sec.TrainingAuthenticationProvider"/>

Next, add the newly defined bean into the authentication chain present in the same configuration file. For example:
	
<authentication-manager>
	...
	<authentication-provider ref="trainingProvider"/>
	...
</authentication-manager>
