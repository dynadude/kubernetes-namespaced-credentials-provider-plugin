package com.cloudbees.jenkins.plugins.kubernetes_namespaced_credentials_provider;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import hudson.model.ItemGroup;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import java.io.InvalidObjectException;
import java.util.List;
import java.util.logging.Logger;
import org.acegisecurity.Authentication;

public class KubernetesSingleNamespacedCredentialsProvider extends CredentialsProvider implements Watcher<Secret> {
    private static final Logger LOG = Logger.getLogger(KubernetesSingleNamespacedCredentialsProvider.class.getName());

    private String namespace;

    private char separator;

    private final KubernetesCredentialsProviderProxy proxy;

    KubernetesSingleNamespacedCredentialsProvider(String namespace, char separator)
            throws NoSuchMethodException, IllegalAccessException, InvalidObjectException, NoSuchFieldException {
        this.namespace = namespace;

        proxy = new KubernetesCredentialsProviderProxy();
        setNamespaceInProxy();

        this.separator = separator;
    }

    private void setNamespaceInProxy()
            throws NoSuchMethodException, IllegalAccessException, InvalidObjectException, NoSuchFieldException {
        NamespacedKubernetesClient client = proxy.getSuperClient();

        proxy.setSuperClient(client.inNamespace(namespace));
    }

    public void startWatchingForSecrets() {
        proxy.startWatchingForSecrets();
    }

    public void stopWatchingForSecrets() {
        proxy.stopWatchingForSecrets();
    }

    @Override
    public void onClose(WatcherException cause) {
        proxy.onClose(cause);
    }

    @Override
    public void eventReceived(Action action, Secret resource) {
        proxy.eventReceived(action, resource);
    }

    @Override
    public <C extends Credentials> List<C> getCredentials(
            Class<C> type, ItemGroup itemGroup, Authentication authentication) {
        List<C> allCredentials = proxy.getCredentials(type, itemGroup, authentication);

        for (C cred : allCredentials) {
            String id = ((IdCredentials) cred).getId();
            if (shouldAddNamespaceToId(id)) {
                try {
                    CredentialsIdChanger.changeId(cred, getIdWithNamespace(id));
                } catch (Exception e) {
                    LOG.severe("Failed to change id for credentials '" + id + "': " + e.getMessage());
                }
            }
        }

        return allCredentials;
    }

    private boolean shouldAddNamespaceToId(String id) {
        return !(id.startsWith(namespace + separator));
    }

    private String getIdWithNamespace(String id) {
        return namespace + separator + id;
    }

    public String getNamespace() {
        return namespace;
    }
}
