import { initializeApp, type FirebaseApp } from "firebase/app";
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  type Auth,
} from "firebase/auth";

let app: FirebaseApp | null = null;
let auth: Auth | null = null;

const getFirebaseAuth = (): Auth => {
  if (!auth) {
    const firebaseConfig = {
      apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
      authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
      projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
      appId: import.meta.env.VITE_FIREBASE_APP_ID,
    };

    if (!firebaseConfig.apiKey || !firebaseConfig.projectId) {
      throw new Error("Firebase config is missing");
    }

    app = initializeApp(firebaseConfig);
    auth = getAuth(app);
  }

  return auth;
};

/**
 * Mở popup đăng nhập Google qua Firebase, trả về Firebase ID token
 * để backend verify và cấp JWT của app.
 */
export const signInWithGoogle = async (): Promise<string> => {
  const provider = new GoogleAuthProvider();
  // Luôn hiện màn hình chọn tài khoản Google mỗi lần đăng nhập
  provider.setCustomParameters({ prompt: "select_account" });
  const result = await signInWithPopup(getFirebaseAuth(), provider);
  return result.user.getIdToken();
};
