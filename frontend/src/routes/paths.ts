/** 라우트 경로는 이 파일 한 곳에 둔다. 화면에서 주소를 문자열로 직접 쓰지 않는다. */
export const routePatterns = {
  home: '/',
  login: '/login',
  myForms: '/me/forms',
  myResponses: '/me/responses',
  myResponse: '/me/responses/:responseId',
  myProfile: '/me/profile',
  publicForm: '/f/:slug',
  form: '/forms/:formId',
  creator: '/creator',
  newForm: '/creator/forms/new',
  editForm: '/creator/forms/:formId/edit',
  formResults: '/creator/forms/:formId/results',
  formExplore: '/creator/forms/:formId/explore',
} as const;

const segment = encodeURIComponent;

export const paths = {
  home: routePatterns.home,
  login: routePatterns.login,
  myForms: routePatterns.myForms,
  myResponses: routePatterns.myResponses,
  myResponse: (responseId: string) => `/me/responses/${segment(responseId)}`,
  myProfile: routePatterns.myProfile,
  publicForm: (slug: string) => `/f/${segment(slug)}`,
  form: (formId: string) => `/forms/${segment(formId)}`,
  creator: routePatterns.creator,
  newForm: routePatterns.newForm,
  editForm: (formId: string) => `/creator/forms/${segment(formId)}/edit`,
  formResults: (formId: string) => `/creator/forms/${segment(formId)}/results`,
  formExplore: (formId: string) => `/creator/forms/${segment(formId)}/explore`,
};
