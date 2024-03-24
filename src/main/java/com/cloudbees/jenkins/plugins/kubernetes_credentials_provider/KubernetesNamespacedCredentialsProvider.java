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
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.init.TermMilestone;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.util.FormValidation;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
@Symbol("kubernetes")
public class KubernetesNamespacedCredentialsProvider extends CredentialsProvider {

    private static final Logger LOG = Logger.getLogger(KubernetesNamespacedCredentialsProvider.class.getName());

    private Set<Namespace> additionalNamespaces = new HashSet<Namespace>();

    private transient Map<String, KubernetesCredentialProvider> providers =
            new ConcurrentHashMap<String, KubernetesCredentialProvider>();

    private transient boolean arePluginsPrepared = false;

    /**
     * A map storing credential scores scoped to ModelObjects, each ModelObject has
     * its own credential store
     */
    private final Map<ModelObject, KubernetesNamespacedCredentialsStore> lazyStoreCache = new HashMap<>();

    private static final char credNameSeparator = '_';

    public KubernetesNamespacedCredentialsProvider() {
        load();

        setAdditionalNamespaces(additionalNamespaces);
    }

    @DataBoundConstructor
    public KubernetesNamespacedCredentialsProvider(Namespace[] additionalNamespaces) {
        setAdditionalNamespaces(additionalNamespaces);
    }

    public Set<Namespace> getAdditionalNamespaces() {
        return Collections.unmodifiableSet(additionalNamespaces);
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) {
        List<Namespace> list = req.bindJSONToList(Namespace.class, json.get("additionalNamespaces"));
        if (!areNamespaceNamesValid(list)) {
            return false;
        }

        setAdditionalNamespaces(list);

        save();

        return true;
    }

    private boolean areNamespaceNamesValid(Collection<Namespace> namespaces) {
        Namespace.DescriptorImpl descriptor = new Namespace.DescriptorImpl();
        for (Namespace namespace : namespaces) {
            if (descriptor.doCheckName(namespace.getName()).equals(FormValidation.ok())) {
                continue;
            }

            return false;
        }

        return true;
    }

    public void setAdditionalNamespaces(Collection<Namespace> additionalNamespaces) {
        setAdditionalNamespaces(additionalNamespaces.toArray(new Namespace[additionalNamespaces.size()]));
    }

    @DataBoundSetter
    public void setAdditionalNamespaces(Namespace[] additionalNamespaces) {
        providers.clear();
        resetAdditionalNamespaces();

        addNamespaces(additionalNamespaces);

        if (arePluginsPrepared) {
            startWatchingForSecrets();
        }
    }

    private void resetAdditionalNamespaces() {
        this.additionalNamespaces = new HashSet<Namespace>();
    }

    private void addNamespaces(Namespace[] namespaces) {
        for (Namespace namespace : namespaces) {
            if (this.additionalNamespaces.contains(namespace)) {
                LOG.warning("Duplicate namespace detected: " + namespace.getName() + ". Ignoring...");
                continue;
            }

            addNamespaceToProviders(namespace);

            this.additionalNamespaces.add(namespace);
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

        LOG.fine("Started watching for secrets in namespaces: "
                + getAdditionalNamespaces().toString());

        arePluginsPrepared = true;
    }

    @hudson.init.Terminator(after = TermMilestone.STARTED)
    @Restricted(NoExternalUse.class) // only for callbacks from Jenkins
    public void stopWatchingForSecrets() {
        for (KubernetesCredentialProvider provider : providers.values()) {
            provider.stopWatchingForSecrets();
        }

        LOG.fine("Stopped watching for secrets in namespaces: "
                + getAdditionalNamespaces().toString());
    }

    @Override
    public <C extends Credentials> List<C> getCredentials(
            Class<C> type, Item item, Authentication authentication, List<DomainRequirement> domainRequirements) {
        // we do not support domain requirements
        return getCredentials(type, item, authentication);
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
    public CredentialsStore getStore(ModelObject object) {
        if (object instanceof ItemGroup<?>) {
            lazyStoreCache.putIfAbsent(object, new KubernetesNamespacedCredentialsStore(this, (ItemGroup<?>) object));
            return lazyStoreCache.get(object);
        }
        return null;
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
        KubernetesSourcedCredential convertSecret(Secret s) {
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
}
