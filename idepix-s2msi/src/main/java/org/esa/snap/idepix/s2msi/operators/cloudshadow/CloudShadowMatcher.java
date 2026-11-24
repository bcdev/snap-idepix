package org.esa.snap.idepix.s2msi.operators.cloudshadow;

/** Chooses a cloud-path offset independently for a connected cloud object. */
interface CloudShadowMatcher {

    CloudShadowMatch match(CloudShadowObject cloud);
}
