---
layout: default
title:  "Kubernetes Namespaced Credentials Provider Plugin"
permalink: /
---

The *Kubernetes Namespaced Credentials Provider* is a [Jenkins](https://jenkins.io) plugin to enable the retrieval of [Credentials](https://plugins.jenkins.io/credentials) stored as [Kubernetes Secrets](https://kubernetes.io/docs/concepts/configuration/secret/) in multiple namespaces.

The plugin supports most common credential types and defines an [`extension point`](https://jenkins.io/doc/developer/extensions/kubernetes-credentials-provider/) that can be implemented by other plugins to add support for custom Credential types. 

# Using

### Pre-requisites

- Jenkins must be running in a kubernetes cluster
- The pod running Jenkins must have a service account with a role that sets the following:
  - get/watch/list permissions for `secrets`[^AWS] in all additional namespaces specified

[^AWS]: it is reported that running in KOPS on AWS you will also need permissions to get/watch/list `configmaps`

#### WARNING
This plugin is much less secure than the normal [Kubernetes Credentials Provider](https://plugins.jenkins.io/kubernetes-credentials-provider) plugin, due to the fact that it requires permissions for multiple namespaces.
<br/>
Because of this fact, it is advisable to enforce a specific naming convention for secrets intended to be used as Jenkins Credentials (such as `jenkins-my-cred-name`), and give jenkins permissions only to secrets that match this convention

## Managing credentials

### Adding credentials

Credentials are added by adding them as secrets to Kubernetes, this is covered in more detail in the [examples](./examples) page.

To restrict the secrets added by this plugin use the system property `com.cloudbees.jenkins.plugins.kubernetes_credentials_provider.KubernetesCredentialProvider.labelSelector`
to set the [Kubernetes Label selector](https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/#label-selectors) expression.

```
-Dcom.cloudbees.jenkins.plugins.kubernetes_credentials_provider.KubernetesCredentialProvider.labelSelector="env in (iat uat)"
```

### Updating credentials

Credentials are updated automatically when changes are made to the Kubernetes secret.

### Deleting credentials

Credentials are deleted automatically when the secret is deleted from Kubernetes. 

### Viewing credentials

Once added the credentials will be visible in Jenkins under the `/credentials/` page.
Any credentials that are loaded from Kubernetes can be identified by the fact that they are stored in the Kubernetes Namespaced Credentials Store.

## Using the credentials inside Jenkins

To use credentials in a pipeline you do not need to do anything special, you access them just as you would for credentials stored in Jenkins. 

for example, if you had the follwing Secret defined in Kubernetes:
{% highlight yaml linenos %}
{% include_relative examples/username-pass.yaml %}
{% endhighlight %}

you could use it via the [Credentials Binding plugin](https://plugins.jenkins.io/credentials-binding) 

{% highlight groovy %}
withCredentials([usernamePassword(credentialsId: 'another-test-usernamepass',
                                  usernameVariable: 'USER', 
                                  passwordVariable: 'PASS')]) {
  sh 'curl -u $USER:$PASS https://some-api/'
}
{% endhighlight %}

or by passing the credentialsId directly to the step requiring a credential:

{% highlight groovy %}
git credentialsId: 'another-test-usernamepass', url: 'https://github.com/foo/bar'
{% endhighlight %}

# Issue reporting

Any issues should be reporting in the main [Jenkins JIRA tracker](https://issues.jenkins-ci.org).
The issue tracker is not a help forum, for help please use [IRC](https://jenkins.io/chat/) or the [user mailing list](https://groups.google.com/forum/#!forum/jenkinsci-users) 

# Releases and Change logs

The [release notes](https://github.com/dynadude/kubernetes-namespaced-credentials-provider-plugin/releases) are managed in GitHub.

# Developing

This [page](./dev/) contains more information on a developer environment.
