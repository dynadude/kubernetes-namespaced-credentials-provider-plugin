package com.cloudbees.jenkins.plugins.kubernetes_namespaced_credentials_provider;

import static org.junit.Assert.*;

import com.cloudbees.jenkins.plugins.kubernetes_credentials_provider.SecretToCredentialConverter;
import com.cloudbees.jenkins.plugins.kubernetes_credentials_provider.convertors.UsernamePasswordCredentialsConvertor;
import com.cloudbees.jenkins.plugins.kubernetes_namespaced_credentials_provider.KubernetesNamespacedCredentialsProvider.KubernetesSingleNamespacedCredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
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
import java.util.Set;
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
    private static final Namespace[] namespaces = {
        new Namespace("test1"), new Namespace("test2"), new Namespace("test3")
    };
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
        KubernetesNamespacedCredentialsProvider provider =
                new KubernetesNamespacedCredentialsProvider(new Namespace[0]);
        Secret[] secrets = getSecrets();

        try {
            addSecretToProvider(secrets[0], namespaces[0], provider);
            throw new Exception("addSecretToProvider shouldn't work with a namespace that does not exist");
        } catch (NoSuchNamespaceException e) {
        }

        verifySecrets(new Namespace[] {}, new Secret[] {}, provider);
    }

    @Test
    public void oneNamespace() throws NoSuchFieldException, IllegalAccessException, InvalidObjectException, Exception {
        KubernetesNamespacedCredentialsProvider provider =
                new KubernetesNamespacedCredentialsProvider(new Namespace[] {namespaces[0]});
        Secret[] secrets = getSecrets();

        verifySecrets(new Namespace[] {}, new Secret[] {}, provider);

        addSecretToProvider(secrets[0], namespaces[0], provider);
        secrets = getSecrets();
        verifySecrets(new Namespace[] {namespaces[0]}, new Secret[] {secrets[0]}, provider);
    }

    @Test
    public void allNamespaces()
            throws NoSuchFieldException, IllegalAccessException, InvalidObjectException, NoSuchNamespaceException {
        KubernetesNamespacedCredentialsProvider provider = new KubernetesNamespacedCredentialsProvider(namespaces);
        Secret[] secrets = getSecrets();

        addSecretToProvider(secrets[0], namespaces[0], provider);
        addSecretToProvider(secrets[1], namespaces[1], provider);
        addSecretToProvider(secrets[2], namespaces[2], provider);

        secrets = getSecrets();
        verifySecrets(namespaces, secrets, provider);
    }

    @Test
    public void deleteSecrets()
            throws NoSuchFieldException, IllegalAccessException, InvalidObjectException, NoSuchNamespaceException {
        KubernetesNamespacedCredentialsProvider provider = new KubernetesNamespacedCredentialsProvider(namespaces);
        Secret[] secrets = getSecrets();

        addSecretToProvider(secrets[0], namespaces[0], provider);
        addSecretToProvider(secrets[1], namespaces[1], provider);
        addSecretToProvider(secrets[2], namespaces[2], provider);

        secrets = getSecrets();
        verifySecrets(namespaces, secrets, provider);

        secrets = getSecrets();
        removeSecretFromProvider(secrets[0], namespaces[0], provider);
        verifySecrets(new Namespace[] {namespaces[1], namespaces[2]}, new Secret[] {secrets[1], secrets[2]}, provider);

        secrets = getSecrets();
        removeSecretFromProvider(secrets[1], namespaces[1], provider);
        secrets = getSecrets();
        verifySecrets(new Namespace[] {namespaces[2]}, new Secret[] {secrets[2]}, provider);
    }

    private void addSecretToProvider(
            Secret secret, Namespace namespace, KubernetesNamespacedCredentialsProvider provider)
            throws NoSuchFieldException, IllegalAccessException, InvalidObjectException, NoSuchNamespaceException {

        sendActionToProvider(Action.ADDED, secret, namespace, provider);
    }

    private void removeSecretFromProvider(
            Secret secret, Namespace namespace, KubernetesNamespacedCredentialsProvider provider)
            throws NoSuchFieldException, IllegalAccessException, InvalidObjectException, NoSuchNamespaceException {

        sendActionToProvider(Action.DELETED, secret, namespace, provider);
    }

    private void sendActionToProvider(
            Action action, Secret secret, Namespace namespace, KubernetesNamespacedCredentialsProvider provider)
            throws NoSuchFieldException, IllegalAccessException, InvalidObjectException, NoSuchNamespaceException {
        Map<String, KubernetesSingleNamespacedCredentialsProvider> providers = getProviders(provider);

        KubernetesSingleNamespacedCredentialsProvider innerProvider = providers.get(namespace.getName());
        if (innerProvider == null) {
            throw new NoSuchNamespaceException("namespace " + namespace.getName()
                    + " was not found in the KubernetesNamespacedCredentialsProvider object");
        }

        innerProvider.eventReceived(action, secret);
    }

    @Test
    public void setNamespacesAndGetNamespaces()
            throws NoSuchFieldException, IllegalAccessException, InvalidObjectException, NoSuchNamespaceException {
        KubernetesNamespacedCredentialsProvider provider =
                new KubernetesNamespacedCredentialsProvider(new Namespace[0]);

        Set<Namespace> namespaces = provider.getAdditionalNamespaces();
        assertEquals("no namespaces", 0, namespaces.size());

        provider.setAdditionalNamespaces(KubernetesNamespacedCredentialsProviderTest.namespaces);

        namespaces = provider.getAdditionalNamespaces();

        assertEquals(
                "three namespaces", KubernetesNamespacedCredentialsProviderTest.namespaces.length, namespaces.size());
        assertTrue("first namespace", namespaces.contains(KubernetesNamespacedCredentialsProviderTest.namespaces[0]));
        assertTrue("second namespace", namespaces.contains(KubernetesNamespacedCredentialsProviderTest.namespaces[1]));
        assertTrue("third namespace", namespaces.contains(KubernetesNamespacedCredentialsProviderTest.namespaces[2]));
    }

    @Test
    public void namespacesWithDuplicates()
            throws NoSuchFieldException, IllegalAccessException, InvalidObjectException, NoSuchNamespaceException {
        KubernetesNamespacedCredentialsProvider provider = new KubernetesNamespacedCredentialsProvider(
                new Namespace[] {namespaces[0], namespaces[1], new Namespace(namespaces[1].getName())});

        Set<Namespace> namespaces = provider.getAdditionalNamespaces();

        assertEquals("two namespaces", 2, namespaces.size());
        assertTrue("first namespace", namespaces.contains(KubernetesNamespacedCredentialsProviderTest.namespaces[0]));
        assertTrue("second namespace", namespaces.contains(KubernetesNamespacedCredentialsProviderTest.namespaces[1]));
    }

    @Test
    public void namespacesInProviders()
            throws NoSuchFieldException, IllegalAccessException, InvalidObjectException, NoSuchNamespaceException {
        KubernetesNamespacedCredentialsProvider provider = new KubernetesNamespacedCredentialsProvider(namespaces);

        Map<String, KubernetesSingleNamespacedCredentialsProvider> providers = getProviders(provider);

        assertEquals("three providers", 3, providers.keySet().size());
        assertEquals(
                "first namespace",
                namespaces[0].getName(),
                providers.get(namespaces[0].getName()).getNamespace());
        assertEquals(
                "second namespace",
                namespaces[1].getName(),
                providers.get(namespaces[1].getName()).getNamespace());
        assertEquals(
                "third namespace",
                namespaces[2].getName(),
                providers.get(namespaces[2].getName()).getNamespace());
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

    private static Secret createSecret(String name, CredentialsScope scope, Namespace namespace) {
        Map<String, String> labels = Map.of(
                "jenkins.io/credentials-scope",
                scope == null ? "global" : scope.name().toLowerCase(Locale.ROOT));
        return createSecret(name, labels, namespace);
    }

    private static Secret createSecret(String name, Map<String, String> labels, Namespace namespace) {
        return createSecret(name, labels, Map.of(), namespace);
    }

    private static Secret createSecret(
            String name, Map<String, String> labels, Map<String, String> annotations, Namespace namespace) {
        Map<String, String> labelsCopy = new HashMap<>(labels);
        labelsCopy.put("jenkins.io/credentials-type", "usernamePassword");

        return new SecretBuilder()
                .withNewMetadata()
                .withNamespace(namespace.getName())
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

    private void verifySecrets(Namespace[] namespaces, Secret[] secrets, CredentialsProvider provider) {
        List<UsernamePasswordCredentials> credentials =
                provider.getCredentials(UsernamePasswordCredentials.class, (ItemGroup) null, ACL.SYSTEM);
        assertEquals("credentials", secrets.length, credentials.size());

        for (int i = 0; i < secrets.length; i++) {
            Secret secret = secrets[i];
            String secretName = secret.getMetadata().getName();
            String namespaceName = namespaces[i].getName();
            assertTrue(
                    "secret " + secretName + " exists",
                    doesSecretExistInCredentials(namespaceName + '_' + secretName, credentials));
        }
    }

    @Test
    public void getSeparatorTest() {
        KubernetesNamespacedCredentialsProvider provider = new KubernetesNamespacedCredentialsProvider(namespaces);

        assertEquals("separator", '_', provider.getSeparator());
    }
}
