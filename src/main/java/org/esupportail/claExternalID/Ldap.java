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
import javax.naming.NamingEnumeration;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.esupportail.claExternalID.Utils.asMap;

import java.util.Collections;
import javax.naming.directory.SearchResult;
import java.util.ArrayList;
import java.util.Enumeration;

class Ldap {
    @SuppressWarnings("serial")
    static class Attrs extends HashMap<String, List<String>> {}

    static class LdapConf {
        String url, bindDN, bindPasswd, peopleDN, principalId, birthdate, mailPerso, mail, civility, family_name, given_name;
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
        rawSetAttribute(ldapConf.principalId + "=" + uid + "," + ldapConf.peopleDN, attr, value);
    }

    void addAttribute(String uid, String attr, String value) throws NamingException, InvalidAttributeIdentifierException, NoPermissionException {
        rawAddAttribute(ldapConf.principalId + "=" + uid + "," + ldapConf.peopleDN, attr, value);
    }

    boolean hasAttribute(String uid, String attr, String value) throws NamingException, InvalidAttributeIdentifierException, NoPermissionException {
        boolean valid = false;
        ArrayList<SearchResult> list = Collections.list(getDirContext().search(ldapConf.peopleDN, "(" + attr + "=" + value + ")", new javax.naming.directory.SearchControls()));
        for (SearchResult result : list) {
            valid = true;
            log.error("The user '" + result.getAttributes().get(ldapConf.principalId).get() + "' already has an '" + attr + "' with value '" + value + "'");
        }
        return valid;
    }

    NamingEnumeration<? extends SearchResult> getEntryForReconciliation(String filter) throws NamingException {
      //(&(up1BirthDay=19500101000000Z)(|(supannMailPerso=johnS@yahoo.fr)(mail=johnS@yahoo.fr))(supannCivilite=M.)(up1BirthName=Smith)(givenName=John))
        log.debug(filter);
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(new String [] {
                  ldapConf.principalId,
                  ldapConf.mailPerso,
                  ldapConf.mail,
                  ldapConf.birthdate,
                  ldapConf.civility,
                  ldapConf.family_name,
                  ldapConf.given_name
        });
        NamingEnumeration<SearchResult> searchRes= getDirContext().search(ldapConf.peopleDN, filter, controls);
        return searchRes == null ? null : searchRes;
    }

    String getSearchResult(ArrayList<? extends SearchResult> searchResultArray) throws NamingException {
      String uid ="";
      if(searchResultArray.size() == 1){
        Attributes attributes = searchResultArray.get(0).getAttributes();
        NamingEnumeration<? extends Attribute> attrs = attributes.getAll();
        uid = (String) attributes.get("uid").get();
        //afficher sur 1 seule ligne les infos LDAP
        String queryresult= "";
        while (attrs.hasMore()) {
            Attribute attr = attrs.next();
            queryresult = queryresult + attr;
            queryresult = queryresult + "\t";
        }
        log.info("LDAP entry found  "+queryresult);
      }else{
        for(SearchResult searchResult:  searchResultArray){
          Attributes attributes = searchResult.getAttributes();
          NamingEnumeration<? extends Attribute> attrs = attributes.getAll();
          String queryresult= "";
          while (attrs.hasMore()) {
              Attribute attr = attrs.next();
              queryresult = queryresult + attr;
              queryresult = queryresult + "\t";
          }
          log.error("LDAP entry found  "+queryresult);
        }
      }
      return uid;
    }

}
