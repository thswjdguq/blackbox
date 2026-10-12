/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "standalone",

  // AI 도우미의 실행 스트림은 이벤트 사이가 30초를 넘을 수 있다. 개발 프록시의 기본 대기(30초)를 넘기면
  // 스트림이 끊기고 화면 쪽 연결은 열린 채 남는다(#102 코멘트). 서버 상한(120초)보다 넉넉히 둔다
  experimental: { proxyTimeout: 150_000 },

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