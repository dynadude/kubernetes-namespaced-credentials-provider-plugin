package com.cloudbees.jenkins.plugins.kubernetes_credentials_provider;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public class Namespace extends AbstractDescribableImpl<Namespace> {
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

    @Override
    public boolean equals(Object namespace) {
        if (!(namespace instanceof Namespace)) {
            return false;
        }

        return getName().equals(((Namespace) namespace).getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public String toString() {
        return getName();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Namespace> {
        public String getDisplayName() {
            return "";
        }

        /**
         * Form validation for a namespace.
         *
         * @param value the name.
         * @return the validation results.
         */
        @Restricted(NoExternalUse.class) // stapler
        public FormValidation doCheckName(@QueryParameter String value) {
            if (StringUtils.isBlank(value)) {
                return FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_MandatoryProperty());
            }

            if (StringUtils.startsWith(value, "-")) {
                return FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_StartsWithDash());
            }

            if (StringUtils.endsWith(value, "-")) {
                return FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_EndsWithDash());
            }

            if (doesStringContainInvalidCharacters(value)) {
                return FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_InvalidCharacters());
            }

            return FormValidation.ok();
        }

        private boolean doesStringContainInvalidCharacters(String string) {
            for (char character : string.toCharArray()) {
                if (isCharacterValid(character)) {
                    continue;
                }

                return true;
            }

            return false;
        }

        private boolean isCharacterValid(char character) {
            if (Character.isLowerCase(character)) {
                return true;
            }

            if (Character.isDigit(character)) {
                return true;
            }

            if (character == '-') {
                return true;
            }

            return false;
        }
    }
}
