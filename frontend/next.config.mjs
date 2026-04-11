/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "standalone",

  // 로컬 개발 전용 API 프록시
  // NEXT_DEV_BACKEND 환경변수가 설정된 경우 /api/* → 백엔드로 전달
  // Docker 빌드 시에는 nginx가 /api 처리하므로 이 설정은 무시됨
  async rewrites() {
    const backend = process.env.NEXT_DEV_BACKEND;
    if (!backend) return [];
    return [
      {
        source: "/api/:path*",
        destination: `${backend}/api/:path*`,
      },
    ];
  },

  images: {
    remotePatterns: [
      {
        protocol: "https",
        hostname: "avatars.githubusercontent.com",
      },
      {
        protocol: "https",
        hostname: "lh3.googleusercontent.com",
      },
    ],
  },
};

export default nextConfig;