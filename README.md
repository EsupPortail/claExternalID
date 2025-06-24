# claExternalID
claExternalID (CAS LDAP Associate External ID) est un outil qui permet d'enregistrer un identifiant externe sur le profil LDAP d'un utilisateur authentifié avec Apereo CAS.

## Intégration dans Apereo CAS

Vous aurez besoin de :
- Placer [le script groovy](./etc/cas/config/interrupt.groovy) à l'emplacement suivant : **etc/cas/config/interrupt.groovy**

- configurer la propriéte suivante dans le fichier de propriété de CAS :

```
cas.interrupt.groovy.location=file:${cas.standalone.configurationDirectory}/interrupt.groovy
```

- Ajouter la dépendance suivante dans le fichier build.gradle :

```
implementation "org.apereo.cas:cas-server-support-interrupt-webflow"
```

### Exemple d'autorisation du service dans Apereo CAS

Créer le fichier [claExternalID_Associate-55.json](./etc/cas/services/claExternalID_Associate-55.json) dans votre dossier 'services/' (CF `cas.service-registry.json.location`) ou via l'application Service Management application.

Il faut aussi configurer Apereo CAS (dans cas.properties) afin que celui-ci retrouve l'utilisateur lors de la seconde authentification :

``` properties
# LDAP
cas.authn.ldap[0].ldapUrl=ldap://ldap.univ.fr
cas.authn.ldap[0].baseDn=ou=users,dc=univ,dc=fr
cas.authn.ldap[0].bindDn=......
cas.authn.ldap[0].bindCredential=**************
cas.authn.ldap[0].principalAttributeId=uid
# on autorise plusieurs types d'identifiants, notamment le mail :
cas.authn.ldap[0].search-filter=(|(uid={user})(supannAliasLogin={user})(mail={user}))
# limite le nombre d'attributs demandés lors du bind
cas.authn.ldap[0].principalAttributeList=

#Usage d'un bind applicatif dédié pour la lecture / écriture depuis le script
cas.custom.properties.claExternalID-ldap-bindDn=......
cas.custom.properties.claExternalID-ldap-bindCredential=**************


# Identities reconciliation
cas.authn.attribute-repository.ldap[0].ldapUrl=ldap://ldap.univ.fr
#cas.authn.attribute-repository.ldap[0].bindDn=
#cas.authn.attribute-repository.ldap[0].bindCredential=
cas.authn.attribute-repository.ldap[0].search-filter=(|(uid={username})(supannAliasLogin={username})(mail={username})(supannFCSub={username}))
# hack pour ne PAS récupérer TOUS les attributs LDAP (permet aussi de mapper les attributs, ex: xxx.ldap[0].attributes.nom=givenName)
cas.authn.attribute-repository.ldap[0].attributes.uid=uid
cas.authn.attribute-repository.ldap[0].attributes.displayName=displayName
cas.authn.attribute-repository.ldap[0].attributes.sn=sn
cas.authn.attribute-repository.ldap[0].attributes.givenName=givenName
cas.authn.attribute-repository.ldap[0].attributes.mail=mail
cas.authn.attribute-repository.ldap[0].attributes.eduPersonAffiliation=eduPersonAffiliation
cas.authn.attribute-repository.ldap[0].attributes.supannEntiteAffectation=supannEntiteAffectation
cas.authn.attribute-repository.ldap[0].attributes.memberOf=memberOf
cas.authn.attribute-repository.ldap[0].attributes.labeledURI=labeledURI
# Par défaut l'attribute-repository utilise en clef un hash non injectif (collisions possibles) conduisant à un mélange de sessions. Depuis CAS 6.2, ces collisions sont moins probables, mais nous vous invitons quand même à désactiver le cache :
cas.authn.attribute-repository.core.expiration-time=0

```


## Fonctionnement technique sous les versions 7.x de Apereo CAS

L'essentiel de cet outil repose enrtièrement sur le mechanisme d'interruption mis à dispostion par CAS, qui permet de reprendre la même cinématique d'éxécution que celle des versions antérieurs :
- CAS vérifie si, après l'authentification OIDC, l'UID est présent (c'est à dire si le supannFCSub existe dans le LDAP)
    - Sinon, vérifie si il est possible de procéder à une réconciliation automatique des identités :
        - Si oui, renvoi vers le service demandé
        - Sinon procéde à une tentative de réconciliation manuelle des identités :
            - Si réussie, renvoi vers le service demandé
            - Sinon bloque l'accès au service
    - Si oui, renvoi vers le service demandé

## Configuration FranceConnect
Toujours dans la configuration Apereo CAS

``` properties

cas.authn.pac4j.oidc[0].generic.typedIdUsed=false
cas.authn.pac4j.oidc[0].generic.principalAttributeId=uid

cas.authn.pac4j.oidc[0].generic.enabled=true
cas.authn.pac4j.oidc[0].generic.name=FranceConnect
cas.authn.pac4j.oidc[0].generic.client-name=FranceConnect
cas.authn.pac4j.oidc[0].generic.scope=openid profile email
cas.authn.pac4j.oidc[0].generic.id=......
cas.authn.pac4j.oidc[0].generic.secret=**************
cas.authn.pac4j.oidc[0].generic.discoveryUri=https://fcp-low.integ01.dev-franceconnect.fr/api/v2/.well-known/openid-configuration
#cas.authn.pac4j.oidc[0].generic.discoveryUri=https://oidc.franceconnect.gouv.fr/api/v2/.well-known/openid-configuration
cas.authn.pac4j.oidc[0].generic.logoutUrl=https://fcp-low.integ01.dev-franceconnect.fr/api/v2/session/end?state=terminateState&post_logout_redirect_uri=https://cas-test.univ.fr/cas/logout
#cas.authn.pac4j.oidc[0].generic.logoutUrl=https://oidc.franceconnect.gouv.fr/api/v2/session/end?state=terminateState&post_logout_redirect_uri=https://cas.univ.fr/cas/logout
cas.authn.pac4j.oidc[0].generic.use-nonce=true
cas.authn.pac4j.oidc[0].generic.disable-pkce=true
cas.authn.pac4j.oidc[0].generic.custom-params.acr_values=eidas1

```

## Mettre à jour les templates Apereo CAS

##### Intégration du bouton FranceConnect
Copier les fichiers [franceconnect-bouton.svg](src/main/resources/static/images/franceconnect-bouton.svg) et [franceconnect-bouton-on-hover.svg](src/main/resources/static/images/franceconnect-bouton-on-hover.svg) dans 'src/main/resources/static/images/'
(ou depuis [la documentation FranceConnect](https://docs.partenaires.franceconnect.gouv.fr/fs/fs-integration/integration-bouton-fc/) )

Et définir les règles CSS suivantes :

```css
#logout-fc {
    width: 100%;
    height: 44px;
    background: #ddd;
    color: black;
    border-color: #ccc;
}

#logout-fc :hover {
    -webkit-filter: brightness(90%);
    filter: brightness(90%);
}

#login-form-controls>.btn-primary, #logout-fc {
    width: 100%;
    border-radius: 4px;
}

#FranceConnect {
    width: 209px;
    height: 56px;
    padding: 0;
    border-radius: 0;
    box-shadow: unset;
    filter: none;
    transition: all 0.4s ease-in-out;
    background-image: url("../images/franceconnect-bouton.svg");
    margin-top: 16px;
}

#FranceConnect:hover, #FranceConnect:focus {
    background-image: url("../images/franceconnect-bouton-on-hover.svg");
    transform: none;
}

#loginProviders .infoFC {
    margin-top: 12px;
    font-size: 14px;
}
```

##### Déconnexion de FranceConnect lors de la demande de réconciliation manuelle
Dans le fichier 'src/main/resources/templates/fragments/loginProviders.html',
juste après la section "authnSourceSection", ajouter :

```html
<section id="authnSourceSection">
    ...
</section>

<div th:replace="~{fragments/submitbutton :: submitButton (messageKey='screen.welcome.button.login')}"></div>
<div th:if="${registeredService != null && registeredService.name == 'claExternalID-associate'}">
<br>
    <input class="mdc-button mdc-button--raised"
        name="cancel"
        id="logout-fc"
        value="Annuler"
        type="button"
        onclick="location.href = '/cas/logout'"
    />
</div>
```
