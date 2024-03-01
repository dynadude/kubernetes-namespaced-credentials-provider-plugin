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
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.acegisecurity.Authentication;

@Extension
public class KubernetesNamespacedCredentialsProvider extends CredentialsProvider {

    private Map<String, KubernetesCredentialProvider> providers = new HashMap<String, KubernetesCredentialProvider>();

    public KubernetesNamespacedCredentialsProvider() {}

    public KubernetesNamespacedCredentialsProvider(String[] namespaces) {
        addNamespaces(namespaces);
    }

    public String[] getNamespaces() {
        return (String[]) providers.keySet().toArray();
    }

    public void setNamespaces(String[] namespaces) {
        providers.clear();

        addNamespaces(namespaces);
    }

    private void addNamespaces(String[] namespaces) {
        for (String namespace : namespaces) {
            providers.put(namespace, new KubernetesSingleNamespacedCredentialsProvider(namespace));
        }
    }

    @Override
    public <C extends Credentials> List<C> getCredentials(
            Class<C> type, ItemGroup itemGroup, Authentication authentication) {
        List<C> allCredentials = new ArrayList<C>();

        for (KubernetesCredentialProvider provider : providers.values()) {
            List<C> credsFromProvider = provider.getCredentials(type, itemGroup, authentication);
            allCredentials.addAll(credsFromProvider);
        }

        return allCredentials;
    }

    @Override
    @NonNull
    public <C extends Credentials> List<C> getCredentials(
            @NonNull Class<C> type, @NonNull Item item, Authentication authentication) {
        return getCredentials(type, item.getParent(), authentication);
    }

    @Override
    public <C extends Credentials> List<C> getCredentials(
            @NonNull Class<C> type,
            @NonNull Item item,
            Authentication authentication,
            List<DomainRequirement> domainRequirements) {
        // we do not support domain requirements
        return getCredentials(type, item, authentication);
    }

    static class KubernetesSingleNamespacedCredentialsProvider extends KubernetesCredentialProvider {
        private String namespace;
        private KubernetesClient client = null;

        KubernetesSingleNamespacedCredentialsProvider(String namespace) {
            this.namespace = namespace;
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
