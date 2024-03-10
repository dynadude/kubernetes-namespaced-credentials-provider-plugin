package com.cloudbees.jenkins.plugins.kubernetes_credentials_provider;

import com.google.common.annotations.VisibleForTesting;
import hudson.CopyOnWrite;
import hudson.Extension;
import hudson.util.FormValidation;
import java.io.Serializable;
import java.util.List;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

@Extension(ordinal = 100)
public class KubernetesNamespacedCredentialsProviderGlobalConfiguration extends GlobalConfiguration
        implements Serializable {

    @CopyOnWrite
    private volatile Namespace[] namespaces = new Namespace[0];

    @VisibleForTesting
    @DataBoundConstructor
    public KubernetesNamespacedCredentialsProviderGlobalConfiguration() {
        load();
    }

    public Namespace[] getNamespaces() {
        return namespaces;
    }

    @DataBoundSetter
    public void setNamespaces(Namespace... namespaces) {
        this.namespaces = namespaces;
        save();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) {
        List<Namespace> list = req.bindJSONToList(Namespace.class, json.get("namespaces"));
        setNamespaces(list.toArray(new Namespace[list.size()]));

        return true;
    }

    public FormValidation doCheckMandatory(@QueryParameter String value) {
        return StringUtils.isBlank(value)
                ? FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_MandatoryProperty())
                : FormValidation.ok();
    }

    public static KubernetesNamespacedCredentialsProviderGlobalConfiguration get() throws ClassNotFoundException {
        KubernetesNamespacedCredentialsProviderGlobalConfiguration config =
                GlobalConfiguration.all().get(KubernetesNamespacedCredentialsProviderGlobalConfiguration.class);
        if (config == null) {
            throw new ClassNotFoundException(
                    "Extension not found for class: KubernetesNamespacedCredentialsProviderGlobalConfiguration");
        }
        return config;
    }
}
