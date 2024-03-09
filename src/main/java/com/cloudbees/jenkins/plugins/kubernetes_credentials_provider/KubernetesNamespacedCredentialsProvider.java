/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.jenkins.plugins.kubernetes_credentials_provider;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.DataBoundConstructor;

@Extension
public class KubernetesNamespacedCredentialsProvider extends CredentialsProvider {

    private static final Logger LOG = Logger.getLogger(KubernetesNamespacedCredentialsProvider.class.getName());

    private Set<String> namespaces = new HashSet<String>();

    private Map<String, KubernetesCredentialProvider> providers = new HashMap<String, KubernetesCredentialProvider>();

    private static final char credNameSeparator = '_';

    public KubernetesNamespacedCredentialsProvider() {}

    @DataBoundConstructor
    public KubernetesNamespacedCredentialsProvider(String[] namespaces) {
        addNamespaces(namespaces);
    }

    public Set<String> getNamespaces() {
        return Collections.unmodifiableSet(namespaces);
    }

    public void setNamespaces(String[] namespaces) {
        providers.clear();
        this.namespaces = new HashSet<String>();

        addNamespaces(namespaces);
    }

    private void addNamespaces(String[] namespaces) {
        for (String namespace : namespaces) {
            if (this.namespaces.contains(namespace)) {
                LOG.warning("Duplicate namespace detected: " + namespace + ". Ignoring...");
                continue;
            }

            addNamespaceToProviders(namespace);

            this.namespaces.add(namespace);
        }
    }

    private void addNamespaceToProviders(String namespace) {
        providers.put(namespace, new KubernetesSingleNamespacedCredentialsProvider(namespace, credNameSeparator));
    }

    @Override
    public <C extends Credentials> List<C> getCredentials(
            Class<C> type, ItemGroup itemGroup, Authentication authentication) {
        List<C> allCredentials = new ArrayList<C>();

        for (CredentialsProvider provider : providers.values()) {
            List<C> credsFromProvider = provider.getCredentials(type, itemGroup, authentication);
            allCredentials.addAll(credsFromProvider);
        }

        return allCredentials;
    }

    @Override
    @NonNull
    public <C extends Credentials> List<C> getCredentials(Class<C> type, Item item, Authentication authentication) {
        return getCredentials(type, item.getParent(), authentication);
    }

    @Override
    public <C extends Credentials> List<C> getCredentials(
            Class<C> type, Item item, Authentication authentication, List<DomainRequirement> domainRequirements) {
        // we do not support domain requirements
        return getCredentials(type, item, authentication);
    }

    static class KubernetesSingleNamespacedCredentialsProvider extends KubernetesCredentialProvider {
        private String namespace;

        private char credNameSeparator;

        @Nullable
        private KubernetesClient client = null;

        KubernetesSingleNamespacedCredentialsProvider(String namespace, char credNameSeparator) {
            this.namespace = namespace;

            this.credNameSeparator = credNameSeparator;
        }

        @Override
        KubernetesClient getKubernetesClient() {
            if (client == null) {
                ConfigBuilder cb = new ConfigBuilder();
                Config config = cb.build();

                config.setNamespace(namespace);

                try (WithContextClassLoader ignored =
                        new WithContextClassLoader(getClass().getClassLoader())) {
                    client = new KubernetesClientBuilder().withConfig(config).build();
                }
            }

            return client;
        }

        @Override
        IdCredentials convertSecret(Secret s) {
            addNamespaceNameToSecret(s);
            return super.convertSecret(s);
        }

        @Override
        public void eventReceived(Action action, Secret secret) {
            if (action == Action.DELETED) {
                addNamespaceNameToSecret(secret);
            }

            super.eventReceived(action, secret);
        }

        private void addNamespaceNameToSecret(Secret s) {
            ObjectMeta metadata = s.getMetadata();
            String previousName = metadata.getName();

            metadata.setName(namespace + credNameSeparator + previousName);
        }
    }

    private static class WithContextClassLoader implements AutoCloseable {

        private final ClassLoader previousClassLoader;

        public WithContextClassLoader(ClassLoader classLoader) {
            this.previousClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(classLoader);
        }

        @Override
        public void close() {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
    }
}
