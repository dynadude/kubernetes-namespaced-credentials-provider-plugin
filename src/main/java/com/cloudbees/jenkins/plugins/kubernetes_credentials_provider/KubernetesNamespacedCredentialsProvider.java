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
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.init.TermMilestone;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

@Extension
public class KubernetesNamespacedCredentialsProvider extends CredentialsProvider {

    private static final Logger LOG = Logger.getLogger(KubernetesNamespacedCredentialsProvider.class.getName());

    private Set<Namespace> namespaces = new HashSet<Namespace>();

    private Map<String, KubernetesCredentialProvider> providers = new HashMap<String, KubernetesCredentialProvider>();

    private static final char credNameSeparator = '_';

    public KubernetesNamespacedCredentialsProvider() {
        load();
    }

    @DataBoundConstructor
    public KubernetesNamespacedCredentialsProvider(Namespace[] namespaces) {
        setNamespaces(namespaces);
    }

    public Set<Namespace> getNamespaces() {
        return Collections.unmodifiableSet(namespaces);
    }

    @DataBoundSetter
    public void setNamespaces(Namespace[] namespaces) {
        providers.clear();
        resetNamespaces();

        addNamespaces(namespaces);
    }

    private void resetNamespaces() {
        this.namespaces = new HashSet<Namespace>();
    }

    private void addNamespaces(Namespace[] namespaces) {
        for (Namespace namespace : namespaces) {
            if (this.namespaces.contains(namespace)) {
                LOG.warning("Duplicate namespace detected: " + namespace.getName() + ". Ignoring...");
                continue;
            }

            addNamespaceToProviders(namespace);

            this.namespaces.add(namespace);
        }
    }

    private void addNamespaceToProviders(Namespace namespace) {
        providers.put(
                namespace.getName(),
                new KubernetesSingleNamespacedCredentialsProvider(namespace.getName(), credNameSeparator));
    }

    @Initializer(after = InitMilestone.PLUGINS_PREPARED, fatal = false)
    @Restricted(NoExternalUse.class) // only for callbacks from Jenkins
    public void startWatchingForSecrets() {
        for (KubernetesCredentialProvider provider : providers.values()) {
            provider.startWatchingForSecrets();
        }

        LOG.fine(
                "Started watching for secrets in namespaces: " + getNamespaces().toString());
    }

    @hudson.init.Terminator(after = TermMilestone.STARTED)
    @Restricted(NoExternalUse.class) // only for callbacks from Jenkins
    public void stopWatchingForSecrets() {
        for (KubernetesCredentialProvider provider : providers.values()) {
            provider.stopWatchingForSecrets();
        }

        LOG.fine(
                "Stopped watching for secrets in namespaces: " + getNamespaces().toString());
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) {
        List<Namespace> list = req.bindJSONToList(Namespace.class, json.get("namespaces"));
        setNamespaces(list.toArray(new Namespace[list.size()]));

        save();

        return true;
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
        private NamespacedKubernetesClient client = null;

        KubernetesSingleNamespacedCredentialsProvider(String namespace, char credNameSeparator) {
            this.namespace = namespace;

            this.credNameSeparator = credNameSeparator;
        }

        public String getNamespace() {
            return namespace;
        }

        @Override
        KubernetesClient getKubernetesClient() throws KubernetesClientException {
            if (client != null) {
                return client;
            }

            KubernetesClient superClient = super.getKubernetesClient();
            if (!(superClient instanceof NamespacedKubernetesClient)) {
                throw new KubernetesClientException(
                        "Kubernetes Client returned by KubernetesCredentialProvider is not an instance of KubernetesClientImpl");
            }

            client = ((NamespacedKubernetesClient) superClient).inNamespace(getNamespace());

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

            metadata.setName(getNamespace() + credNameSeparator + previousName);
        }
    }

    @Symbol("kubernetesCredentialsNamespaces")
    public static class DescriptorImpl extends Descriptor<CredentialsProvider> {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.KubernetesNamespacedCredentialsProvider_DisplayName();
        }
    }
}
