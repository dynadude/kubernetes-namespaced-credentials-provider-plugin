package com.cloudbees.jenkins.plugins.kubernetes_credentials_provider;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.io.Serializable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class Namespace extends AbstractDescribableImpl<Namespace> implements Serializable {
    private String name;

    @DataBoundConstructor
    public Namespace(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @DataBoundSetter
    public void setName(String name) {
        this.name = name;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Namespace> {
        public String getDisplayName() {
            return "";
        }
    }
}
