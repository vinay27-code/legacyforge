export interface UserSummary {
  id: string;
  email: string;
  role: 'USER' | 'REVIEWER' | 'ADMIN';
}

export interface AuthResponse {
  accessToken: string;
  expiresInSeconds: number;
  user: UserSummary;
}

export interface RegisterRequest {
  email: string;
  password: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}
