package org.esupportail.claExternalID;


class Conf {
    String cas_base_url;
    String claExternalID_url;
    String refId_attribute;
    String refId_prefix;

    // below have valid default values
    String cas_login_url;
    String cas_logout_url;
    String ent_logout_url;

    Ldap.LdapConf ldap;
    
    Conf init() {
        if (cas_base_url == null) throw new RuntimeException("config.json must set cas_base_url");
        if (claExternalID_url == null) throw new RuntimeException("config.json must set casLdapAssociateExternalID_url");
        if (cas_login_url == null) cas_login_url = cas_base_url + "/login";
        if (cas_logout_url == null) cas_logout_url = cas_base_url + "/logout";
        return this;
    }
}
