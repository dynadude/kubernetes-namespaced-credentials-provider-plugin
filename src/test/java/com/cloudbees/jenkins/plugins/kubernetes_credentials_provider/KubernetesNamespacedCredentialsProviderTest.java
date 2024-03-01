package com.cloudbees.jenkins.plugins.kubernetes_credentials_provider;

import static org.junit.Assert.*;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.ExtensionList;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import jenkins.util.Timer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesNamespacedCredentialsProviderTest {

    private static final Long EVENT_WAIT_PERIOD_MS = 10L;

    public @Rule KubernetesServer server = new KubernetesServer();
    private @Mock ScheduledExecutorService jenkinsTimer;

    private @Mock(answer = Answers.CALLS_REAL_METHODS) MockedStatic<ExtensionList> extensionList;
    private @Mock MockedStatic<Timer> timer;

    @Test
    public void startWatchingForSecrets() {
        Secret s1 = createSecret("s1", (CredentialsScope) null);
        Secret s2 = createSecret("s2", (CredentialsScope) null);
        Secret s3 = createSecret("s3", (CredentialsScope) null);

        // returns s1 and s3, the credentials map should be reset to this list
        server.expect()
                .withPath("/api/v1/namespaces/test/secrets?labelSelector=jenkins.io%2Fcredentials-type")
                .andReturn(
                        200,
                        new SecretListBuilder()
                                .withNewMetadata()
                                .withResourceVersion("1")
                                .endMetadata()
                                .addToItems(s1, s3)
                                .build())
                .once();

        // expect the s2 will get dropped when the credentials map is reset to the full
        // list
        server.expect()
                .withPath(
                        "/api/v1/namespaces/test/secrets?labelSelector=jenkins.io%2Fcredentials-type&resourceVersion=1&allowWatchBookmarks=true&watch=true")
                .andUpgradeToWebSocket()
                .open()
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(s1, "ADDED"))
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(s2, "ADDED"))
                .done()
                .once();

        String[] namespaces = {"test"};
        KubernetesNamespacedCredentialsProvider provider = new KubernetesNamespacedCredentialsProvider(namespaces);

        List<UsernamePasswordCredentials> credentials =
                provider.getCredentials(UsernamePasswordCredentials.class, (ItemGroup) null, ACL.SYSTEM);
        assertEquals("credentials", 2, credentials.size());
        assertTrue("secret s1 exists", credentials.stream().anyMatch(c -> "s1"
                .equals(((UsernamePasswordCredentialsImpl) c).getId())));
        assertTrue("secret s3 exists", credentials.stream().anyMatch(c -> "s3"
                .equals(((UsernamePasswordCredentialsImpl) c).getId())));
    }

    private Secret createSecret(String name, CredentialsScope scope) {
        Map<String, String> labels = Map.of(
                "jenkins.io/credentials-scope",
                scope == null ? "global" : scope.name().toLowerCase(Locale.ROOT));
        return createSecret(name, labels);
    }

    private Secret createSecret(String name, Map<String, String> labels) {
        return createSecret(name, labels, Map.of());
    }

    private Secret createSecret(String name, Map<String, String> labels, Map<String, String> annotations) {
        Map<String, String> labelsCopy = new HashMap<>(labels);
        labelsCopy.put("jenkins.io/credentials-type", "usernamePassword");

        return new SecretBuilder()
                .withNewMetadata()
                .withNamespace("test")
                .withName(name)
                .addToLabels(labelsCopy)
                .addToAnnotations(annotations)
                .endMetadata()
                .addToData("username", "bXlVc2VybmFtZQ==")
                .addToData("password", "UGEkJHdvcmQ=")
                .build();
    }
}
