import type { NextConfig } from "next";

const apiDestination = (
  process.env.NEXT_API_DESTINATION ?? "http://localhost:8080/api/v1"
).replace(/\/$/, "");

const nextConfig: NextConfig = {
  /* config options here */
  output: "standalone",
  reactCompiler: true,
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${apiDestination}/:path*`
      }
    ];
  }
};

export default nextConfig;
