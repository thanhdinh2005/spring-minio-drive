import api from "./api";

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  displayName: string;
}

export interface TokenResponse {
  accessToken: string;
  expiredIn: number;
}

export interface RegisterResponse {
  id: string;
  email: string;
  displayName: string;
  plan: "FREE" | "PREMIUM";
}

export interface AppResponse<T> {
  success: boolean;
  status: number;
  code?: string;
  message?: string;
  data: T;
  timestamp: string;
}

export const login = async (
  data: LoginRequest
): Promise<TokenResponse> => {
  const response = await api.post<AppResponse<TokenResponse>>(
    "/auth/login",
    data
  );

  return response.data.data;
};

export const googleLogin = async (
  idToken: string
): Promise<TokenResponse> => {
  const response = await api.post<AppResponse<TokenResponse>>(
    "/auth/google",
    { idToken }
  );

  return response.data.data;
};

export const verifyEmail = async (token: string): Promise<void> => {
  await api.get("/auth/verify-email", { params: { token } });
};

export const resendVerification = async (email: string): Promise<void> => {
  await api.post("/auth/resend-verification", { email });
};

export const register = async (
  data: RegisterRequest
): Promise<RegisterResponse> => {
  const response = await api.post<AppResponse<RegisterResponse>>(
    "/auth/register",
    data
  );

  return response.data.data;
};
