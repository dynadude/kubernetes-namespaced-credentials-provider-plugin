# kubernetes-namespaced-credentials-provider-plugin

The *Kubernetes Namespaced Credentials Provider* is a [Jenkins](https://jenkins.io) plugin to enable the retrieval of [Credentials](https://plugins.jenkins.io/credentials) stored as [Kubernetes Secrets](https://kubernetes.io/docs/concepts/configuration/secret/) in multiple namespaces.

The plugin depends on the [Kubernetes Credentials Provider](https://plugins.jenkins.io/kubernetes-credentials-provider) plugin and manages multiple instances of [KubernetesCredentialProvider](https://github.com/jenkinsci/kubernetes-credentials-provider-plugin/blob/master/src/main/java/com/cloudbees/jenkins/plugins/kubernetes_credentials_provider/KubernetesCredentialProvider.java), each in its own Kubernetes Namespace.

## Managing additional namespaces

### Through the UI

Go to `Manage Jenkins` -> `System`, and look for the `Kubernetes Credentials Additional Namespaces` section.
<br/>
There, you can add and remove namespaces.

### Through Jenkins Configuration as Code

Create a `configScript` with the content:

```yaml
credentials:
  kubernetes:
    additionalNamespaces:
    - name: "my-first-namespace"
    - name: "my-second-namespace"
```
