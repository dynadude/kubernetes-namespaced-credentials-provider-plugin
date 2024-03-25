package com.cloudbees.jenkins.plugins.kubernetes_namespaced_credentials_provider;

import com.cloudbees.jenkins.plugins.kubernetes_credentials_provider.KubernetesCredentialProvider;
import com.cloudbees.jenkins.plugins.kubernetes_credentials_provider.KubernetesCredentialsStore;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.CredentialsStoreAction;
import com.cloudbees.plugins.credentials.domains.Domain;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;

public class KubernetesNamespacedCredentialsStore extends CredentialsStore {

    private final KubernetesNamespacedCredentialsProvider provider;

    // used to use its methods (can't inherit from it)
    private final KubernetesCredentialsStore store;

    public KubernetesNamespacedCredentialsStore(
            KubernetesNamespacedCredentialsProvider provider, ItemGroup<?> context) {
        super(KubernetesNamespacedCredentialsProvider.class);
        this.provider = provider;

        store = new KubernetesCredentialsStore(new KubernetesCredentialProvider(), context);
    }

    @Override
    public ModelObject getContext() {
        return store.getContext();
    }

    @Override
    public boolean hasPermission(Authentication a, Permission permission) {
        return store.hasPermission(a, permission);
    }

    @NonNull
    @Override
    public List<Credentials> getCredentials(@NonNull Domain domain) {
        // Only the global domain is supported
        if (!Domain.global().equals(domain)) {
            return Collections.emptyList();
        }

        AccessControlled ac = getAccessControlledContext();
        if (ac == null) {
            if (Jenkins.getInstance().getACL().hasPermission(CredentialsProvider.VIEW)) {
                return provider.getCredentials(Credentials.class, (ItemGroup<?>) store.getContext(), ACL.SYSTEM);
            }
        } else {
            if (ac.hasPermission(CredentialsProvider.VIEW)) {
                return provider.getCredentials(Credentials.class, (ItemGroup<?>) store.getContext(), ACL.SYSTEM);
            }
        }

        return Collections.emptyList();
    }

    @Nullable
    private AccessControlled getAccessControlledContext() {
        AccessControlled ac = null;
        ItemGroup<?> ig = (ItemGroup<?>) store.getContext();
        while (ac == null) {
            if (ig instanceof AccessControlled) {
                ac = (AccessControlled) ig;
            } else if (ig instanceof Item) {
                ig = ((Item) ig).getParent();
            } else {
                break;
            }
        }
        return ac;
    }

    @Override
    public boolean addCredentials(@NonNull Domain domain, @NonNull Credentials credentials) {
        return store.addCredentials(domain, credentials);
    }

    @Override
    public boolean removeCredentials(@NonNull Domain domain, @NonNull Credentials credentials) {
        return store.removeCredentials(domain, credentials);
    }

    @Override
    public boolean updateCredentials(
            @NonNull Domain domain, @NonNull Credentials current, @NonNull Credentials replacement) {
        return store.updateCredentials(domain, current, replacement);
    }

    @Nullable
    @Override
    public CredentialsStoreAction getStoreAction() {
        return store.getStoreAction();
    }
}
