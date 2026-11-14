/**
 * Application version configuration.
 * Reads directly from gradle.properties at build time via next.config.mjs
 */
export const CURRENT_APP_VERSION = process.env.NEXT_PUBLIC_APP_VERSION || "4.3.0";
export const CURRENT_APP_TAG = `v${CURRENT_APP_VERSION}`;
