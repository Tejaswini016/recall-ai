import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Emits a self-contained server bundle used by the Docker image.
  output: "standalone",
  reactStrictMode: true,
};

export default nextConfig;
