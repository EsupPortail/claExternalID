package org.esupportail.claExternalID;

import java.util.List;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.InvalidAttributeIdentifierException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.esupportail.claExternalID.Utils.asMap;
    
class Ldap {
    @SuppressWarnings("serial")
    static class Attrs extends HashMap<String, List<String>> {}

    static class LdapConf {
        String url, bindDN, bindPasswd, peopleDN;
    }
    LdapConf ldapConf;
    DirContext dirContext;
    Log log = LogFactory.getLog(Ldap.class);

    Ldap(LdapConf ldapConf) {
        this.ldapConf = ldapConf;
    }
    
    private DirContext ldap_connect() {
        Map<String,String> env =
            asMap(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
             .add(Context.PROVIDER_URL, ldapConf.url)
             .add(Context.SECURITY_AUTHENTICATION, "simple")
             .add(Context.SECURITY_PRINCIPAL, ldapConf.bindDN)
             .add(Context.SECURITY_CREDENTIALS, ldapConf.bindPasswd);

        try {
            return new InitialDirContext(new Hashtable<>(env));
        } catch (NamingException e) {
            log.error("error connecting to ldap server", e);
            throw new RuntimeException("error connecting to ldap server");
        }
    }

    synchronized DirContext getDirContext() {
        if (dirContext == null) {
            dirContext = ldap_connect();
        }
        return dirContext;
    }

    private Attribute rawGetAttribute(String name, String attr) throws NamingException {
	Attributes attrs = getDirContext().getAttributes(name, new String[] { attr });
	return attrs == null ? null : attrs.get(attr);
    }

    private void rawSetAttribute(String name, String attr, String value) throws NamingException, InvalidAttributeIdentifierException, NoPermissionException {
	getDirContext().modifyAttributes(name, DirContext.REPLACE_ATTRIBUTE, new BasicAttributes(attr, value));
    }

    private void rawAddAttribute(String name, String attr, String value) throws NamingException, InvalidAttributeIdentifierException, NoPermissionException {
        getDirContext().modifyAttributes(name, DirContext.ADD_ATTRIBUTE, new BasicAttributes(attr, value));
    }

    void setAttribute(String uid, String attr, String value) throws NamingException, InvalidAttributeIdentifierException, NoPermissionException {
        rawSetAttribute("uid=" + uid + "," + ldapConf.peopleDN, attr, value);
    }

    void addAttribute(String uid, String attr, String value) throws NamingException, InvalidAttributeIdentifierException, NoPermissionException {
        rawAddAttribute("uid=" + uid + "," + ldapConf.peopleDN, attr, value);
    }    

}
