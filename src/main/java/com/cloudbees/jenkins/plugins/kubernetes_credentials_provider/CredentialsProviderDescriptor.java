package com.cloudbees.jenkins.plugins.kubernetes_credentials_provider;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

/**
 * {@link Descriptor} for {@link BuildDiscarder}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class CredentialsProviderDescriptor extends Descriptor<CredentialsProvider> {
    protected CredentialsProviderDescriptor(Class clazz) {
        super(clazz);
    }

    protected CredentialsProviderDescriptor() {}

    /**
     * Returns all the registered {@link BuildDiscarderDescriptor}s.
     */
    public static DescriptorExtensionList<CredentialsProvider, CredentialsProviderDescriptor> all() {
        return Jenkins.get().getDescriptorList(CredentialsProvider.class);
    }
}
