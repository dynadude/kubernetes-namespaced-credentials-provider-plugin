package com.cloudbees.jenkins.plugins.kubernetes_credentials_provider;

import static org.junit.Assert.*;

import com.cloudbees.jenkins.plugins.kubernetes_credentials_provider.KubernetesNamespacedCredentialsProvider.KubernetesSingleNamespacedCredentialsProvider;
import com.cloudbees.jenkins.plugins.kubernetes_credentials_provider.convertors.UsernamePasswordCredentialsConvertor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.ExtensionList;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.Watcher.Action;
import java.io.InvalidObjectException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesNamespacedCredentialsProviderTest {
    private static final String[] namespaces = {"test1", "test2", "test3"};
    private @Mock ScheduledExecutorService jenkinsTimer;

    private @Mock(answer = Answers.CALLS_REAL_METHODS) MockedStatic<ExtensionList> extensionList;

    @Before
    public void setUp() {
        // mocked to validate start watching for secrets
        ExtensionList<SecretToCredentialConverter> converters =
                ExtensionList.create((Jenkins) null, SecretToCredentialConverter.class);
        converters.addAll(Collections.singleton(new UsernamePasswordCredentialsConvertor()));
        extensionList
                .when(() -> ExtensionList.lookup(SecretToCredentialConverter.class))
                .thenReturn(converters);
    }

    @Test
    public void noNamespaces() throws NoSuchFieldException, IllegalAccessException, InvalidObjectException, Exception {
        KubernetesNamespacedCredentialsProvider provider = new KubernetesNamespacedCredentialsProvider();
        Secret[] secrets = getSecrets();

        try {
            addSecretToProvider(secrets[0], namespaces[0], provider);
            throw new Exception("addSecretToProvider shouldn't work with a namespace that does not exist");
        } catch (NoSuchNamespaceException e) {
        }

        List<UsernamePasswordCredentials> credentials =
                provider.getCredentials(UsernamePasswordCredentials.class, (ItemGroup) null, ACL.SYSTEM);
        assertEquals("credentials", 0, credentials.size());
        assertFalse("secret s1 exists", doesSecretExistInCredentials(namespaces[0] + '_' + "s1", credentials));
    }

    @Test
    public void oneNamespace() throws NoSuchFieldException, IllegalAccessException, InvalidObjectException, Exception {
        KubernetesNamespacedCredentialsProvider provider =
                new KubernetesNamespacedCredentialsProvider(new String[] {namespaces[0]});
        Secret[] secrets = getSecrets();

        List<UsernamePasswordCredentials> credentials =
                provider.getCredentials(UsernamePasswordCredentials.class, (ItemGroup) null, ACL.SYSTEM);
        assertEquals("credentials", 0, credentials.size());
        assertFalse("secret s1 exists", doesSecretExistInCredentials(namespaces[0] + '_' + "s1", credentials));

        addSecretToProvider(secrets[0], namespaces[0], provider);

        credentials = provider.getCredentials(UsernamePasswordCredentials.class, (ItemGroup) null, ACL.SYSTEM);

        assertEquals("credentials", 1, credentials.size());
        assertTrue("secret s1 exists", doesSecretExistInCredentials(namespaces[0] + '_' + "s1", credentials));
    }

    @Test
    public void allNamespaces()
            throws NoSuchFieldException, IllegalAccessException, InvalidObjectException, NoSuchNamespaceException {
        KubernetesNamespacedCredentialsProvider provider = new KubernetesNamespacedCredentialsProvider(namespaces);
        Secret[] secrets = getSecrets();

        addSecretToProvider(secrets[0], namespaces[0], provider);
        addSecretToProvider(secrets[1], namespaces[1], provider);
        addSecretToProvider(secrets[2], namespaces[2], provider);

        List<UsernamePasswordCredentials> credentials =
                provider.getCredentials(UsernamePasswordCredentials.class, (ItemGroup) null, ACL.SYSTEM);
        assertEquals("credentials", 3, credentials.size());
        assertTrue("secret s1 exists", doesSecretExistInCredentials(namespaces[0] + '_' + "s1", credentials));
        assertTrue("secret s2 exists", doesSecretExistInCredentials(namespaces[1] + '_' + "s2", credentials));
        assertTrue("secret s3 exists", doesSecretExistInCredentials(namespaces[2] + '_' + "s3", credentials));
    }

    private void addSecretToProvider(Secret secret, String namespace, KubernetesNamespacedCredentialsProvider provider)
            throws NoSuchFieldException, IllegalAccessException, InvalidObjectException, NoSuchNamespaceException {

        sendActionToProvider(Action.ADDED, secret, namespace, provider);
    }

    @Test
    public void deleteSecrets()
            throws NoSuchFieldException, IllegalAccessException, InvalidObjectException, NoSuchNamespaceException {
        KubernetesNamespacedCredentialsProvider provider = new KubernetesNamespacedCredentialsProvider(namespaces);
        Secret[] secrets = getSecrets();

        addSecretToProvider(secrets[0], namespaces[0], provider);
        addSecretToProvider(secrets[1], namespaces[1], provider);
        addSecretToProvider(secrets[2], namespaces[2], provider);

        List<UsernamePasswordCredentials> credentials =
                provider.getCredentials(UsernamePasswordCredentials.class, (ItemGroup) null, ACL.SYSTEM);
        assertEquals("credentials", 3, credentials.size());
        assertTrue("secret s1 exists", doesSecretExistInCredentials(namespaces[0] + '_' + "s1", credentials));
        assertTrue("secret s2 exists", doesSecretExistInCredentials(namespaces[1] + '_' + "s2", credentials));
        assertTrue("secret s3 exists", doesSecretExistInCredentials(namespaces[2] + '_' + "s3", credentials));

        secrets = getSecrets();

        removeSecretFromProvider(secrets[0], namespaces[0], provider);

        credentials = provider.getCredentials(UsernamePasswordCredentials.class, (ItemGroup) null, ACL.SYSTEM);
        assertEquals("credentials", 2, credentials.size());
        assertFalse("secret s1 exists", doesSecretExistInCredentials(namespaces[0] + '_' + "s1", credentials));
        assertTrue("secret s2 exists", doesSecretExistInCredentials(namespaces[1] + '_' + "s2", credentials));
        assertTrue("secret s3 exists", doesSecretExistInCredentials(namespaces[2] + '_' + "s3", credentials));

        secrets = getSecrets();

        removeSecretFromProvider(secrets[1], namespaces[1], provider);

        credentials = provider.getCredentials(UsernamePasswordCredentials.class, (ItemGroup) null, ACL.SYSTEM);
        assertEquals("credentials", 1, credentials.size());
        assertFalse("secret s1 exists", doesSecretExistInCredentials(namespaces[0] + '_' + "s1", credentials));
        assertFalse("secret s2 exists", doesSecretExistInCredentials(namespaces[1] + '_' + "s2", credentials));
        assertTrue("secret s3 exists", doesSecretExistInCredentials(namespaces[2] + '_' + "s3", credentials));
    }

    private void removeSecretFromProvider(
            Secret secret, String namespace, KubernetesNamespacedCredentialsProvider provider)
            throws NoSuchFieldException, IllegalAccessException, InvalidObjectException, NoSuchNamespaceException {

        sendActionToProvider(Action.DELETED, secret, namespace, provider);
    }

    private void sendActionToProvider(
            Action action, Secret secret, String namespace, KubernetesNamespacedCredentialsProvider provider)
            throws NoSuchFieldException, IllegalAccessException, InvalidObjectException, NoSuchNamespaceException {
        Map<String, KubernetesSingleNamespacedCredentialsProvider> providers = getProviders(provider);

        KubernetesSingleNamespacedCredentialsProvider innerProvider = providers.get(namespace);
        if (innerProvider == null) {
            throw new NoSuchNamespaceException(
                    "namespace " + namespace + " was not found in the KubernetesNamespacedCredentialsProvider object");
        }

        innerProvider.eventReceived(action, secret);
    }

    private Secret[] getSecrets() {
        return new Secret[] {
            createSecret("s1", (CredentialsScope) null, namespaces[0]),
            createSecret("s2", (CredentialsScope) null, namespaces[1]),
            createSecret("s3", (CredentialsScope) null, namespaces[2])
        };
    }

    private Map<String, KubernetesSingleNamespacedCredentialsProvider> getProviders(
            KubernetesNamespacedCredentialsProvider provider)
            throws NoSuchFieldException, IllegalAccessException, InvalidObjectException {
        Field providersField = KubernetesNamespacedCredentialsProvider.class.getDeclaredField("providers");

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

    private static Secret createSecret(String name, CredentialsScope scope, String namespace) {
        Map<String, String> labels = Map.of(
                "jenkins.io/credentials-scope",
                scope == null ? "global" : scope.name().toLowerCase(Locale.ROOT));
        return createSecret(name, labels, namespace);
    }

    private static Secret createSecret(String name, Map<String, String> labels, String namespace) {
        return createSecret(name, labels, Map.of());
    }

    private static Secret createSecret(String name, Map<String, String> labels, Map<String, String> annotations) {
        return createSecret(name, labels, annotations, "test");
    }

    private static Secret createSecret(
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

    private boolean doesSecretExistInCredentials(String secretName, List<UsernamePasswordCredentials> credentials) {
        return credentials.stream().anyMatch(c -> secretName.equals(((UsernamePasswordCredentialsImpl) c).getId()));
    }
}
