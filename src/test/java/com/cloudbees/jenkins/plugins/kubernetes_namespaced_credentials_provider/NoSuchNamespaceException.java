package com.cloudbees.jenkins.plugins.kubernetes_namespaced_credentials_provider;

class NoSuchNamespaceException extends Exception {
    public NoSuchNamespaceException(String message) {
        super(message);
    }
}
