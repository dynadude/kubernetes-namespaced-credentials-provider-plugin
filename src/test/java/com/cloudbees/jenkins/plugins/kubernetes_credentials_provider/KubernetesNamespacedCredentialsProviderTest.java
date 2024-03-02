package com.cloudbees.jenkins.plugins.kubernetes_credentials_provider;

import static org.junit.Assert.*;

import com.cloudbees.jenkins.plugins.kubernetes_credentials_provider.KubernetesNamespacedCredentialsProvider.KubernetesSingleNamespacedCredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.Watcher.Action;
import java.io.InvalidObjectException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesNamespacedCredentialsProviderTest {
    private static final String[] namespaces = {"test1", "test2", "test3"};

    @Test
    public void startWatchingForSecrets()
            throws NoSuchFieldException, IllegalAccessException, InvalidObjectException, NoSuchNamespaceException {
        KubernetesNamespacedCredentialsProvider provider = new KubernetesNamespacedCredentialsProvider(namespaces);

        Secret s1 = createSecret("s1", (CredentialsScope) null, namespaces[0]);
        Secret s2 = createSecret("s2", (CredentialsScope) null, namespaces[1]);
        Secret s3 = createSecret("s3", (CredentialsScope) null, namespaces[2]);

        addSecretToProvider(s1, namespaces[0], provider);
        addSecretToProvider(s2, namespaces[1], provider);
        addSecretToProvider(s3, namespaces[2], provider);

        List<UsernamePasswordCredentials> credentials =
                provider.getCredentials(UsernamePasswordCredentials.class, (ItemGroup) null, ACL.SYSTEM);
        assertEquals("credentials", 2, credentials.size());
        assertTrue("secret s1 exists", credentials.stream().anyMatch(c -> "s1"
                .equals(((UsernamePasswordCredentialsImpl) c).getId())));
        assertTrue("secret s3 exists", credentials.stream().anyMatch(c -> "s3"
                .equals(((UsernamePasswordCredentialsImpl) c).getId())));
    }

    private void addSecretToProvider(Secret secret, String namespace, KubernetesNamespacedCredentialsProvider provider)
            throws NoSuchFieldException, IllegalAccessException, InvalidObjectException, NoSuchNamespaceException {

        Map<String, KubernetesSingleNamespacedCredentialsProvider> providers = getProviders(provider);

        KubernetesSingleNamespacedCredentialsProvider innerProvider = providers.get(namespace);
        if (innerProvider == null) {
            throw new NoSuchNamespaceException(
                    "namespace " + namespace + " was not found in the KubernetesNamespacedCredentialsProvider object");
        }

        innerProvider.eventReceived(Action.ADDED, secret);
    }

    private Map<String, KubernetesSingleNamespacedCredentialsProvider> getProviders(
            KubernetesNamespacedCredentialsProvider provider)
            throws NoSuchFieldException, IllegalAccessException, InvalidObjectException {
        Field providersField = KubernetesNamespacedCredentialsProvider.class.getDeclaredField("providers");

        // Set the accessibility as true
        providersField.setAccessible(true);

        Object providersObject = null;
        try {
            providersObject = providersField.get(provider);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessException(
                    "The 'providers' field of KubernetesNamespacedCredentialsProvider is not correctly set to accessible");
        }

        if (!(providersObject instanceof Map<?, ?>)) {
            throw new InvalidObjectException(
                    "The 'providers' field of KubernetesNamespacedCredentialsProvider is not of type Map");
        }

        Map<String, KubernetesSingleNamespacedCredentialsProvider> providers =
                (Map<String, KubernetesSingleNamespacedCredentialsProvider>) providersObject;

        providersField.setAccessible(false);

        return providers;
    }

    private Secret createSecret(String name, CredentialsScope scope, String namespace) {
        Map<String, String> labels = Map.of(
                "jenkins.io/credentials-scope",
                scope == null ? "global" : scope.name().toLowerCase(Locale.ROOT));
        return createSecret(name, labels, namespace);
    }

    private Secret createSecret(String name, Map<String, String> labels, String namespace) {
        return createSecret(name, labels, Map.of());
    }

    private Secret createSecret(String name, Map<String, String> labels, Map<String, String> annotations) {
        return createSecret(name, labels, annotations, "test");
    }

    private Secret createSecret(
            String name, Map<String, String> labels, Map<String, String> annotations, String namespace) {
        Map<String, String> labelsCopy = new HashMap<>(labels);
        labelsCopy.put("jenkins.io/credentials-type", "usernamePassword");

        return new SecretBuilder()
                .withNewMetadata()
                .withNamespace(namespace)
                .withName(name)
                .addToLabels(labelsCopy)
                .addToAnnotations(annotations)
                .endMetadata()
                .addToData("username", "bXlVc2VybmFtZQ==")
                .addToData("password", "UGEkJHdvcmQ=")
                .build();
    }
}
