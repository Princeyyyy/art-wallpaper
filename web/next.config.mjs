import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Single source of truth: read appVersion dynamically from gradle.properties
let appVersion = "4.2.3";
try {
  const gradlePropsPath = path.resolve(__dirname, "../gradle.properties");
  if (fs.existsSync(gradlePropsPath)) {
    const content = fs.readFileSync(gradlePropsPath, "utf8");
    const match = content.match(/appVersion=([^\r\n]+)/);
    if (match && match[1]) {
      appVersion = match[1].trim();
    }
  }
} catch (e) {
  console.warn("Could not read gradle.properties for appVersion, using fallback:", e);
}

/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "export",
  outputFileTracingRoot: __dirname,
  env: {
    NEXT_PUBLIC_APP_VERSION: appVersion,
  },
  images: {
    unoptimized: true,
  },
};

export default nextConfig;
