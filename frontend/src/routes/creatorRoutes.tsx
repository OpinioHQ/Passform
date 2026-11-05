import { useNavigate, useParams } from 'react-router-dom';
import { useDemoData } from '../demo/DemoDataContext';
import { demoBlankForm, demoForm } from '../demo/data';
import { BuilderPage } from '../pages/BuilderPage';
import { CreatedPage } from '../pages/CreatedPage';
import { ExplorePage } from '../pages/ExplorePage';
import { paths } from './paths';

export function CreatedFormsRoute() {
  const navigate = useNavigate();
  return (
    <CreatedPage
      onCreate={() => navigate(paths.newForm)}
      onEdit={() => navigate(paths.editForm(demoForm.form_id))}
      onExplore={() => navigate(paths.formExplore(demoForm.form_id))}
    />
  );
}

/** 실제 빌더와 저장은 E3에서 만든다. 여기서는 빈 폼으로 화면만 연다. */
export function NewFormRoute() {
  return <BuilderPage initial={demoBlankForm} isNew />;
}

export function EditFormRoute() {
  const { formId = '' } = useParams();
  const navigate = useNavigate();
  return (
    <BuilderPage
      initial={demoForm}
      onPreview={() => navigate(paths.form(formId))}
      onExplore={() => navigate(paths.formExplore(formId))}
    />
  );
}

export function FormExploreRoute() {
  const { formId = '' } = useParams();
  const navigate = useNavigate();
  const { responses } = useDemoData();
  return <ExplorePage responses={responses} onBuilder={() => navigate(paths.editForm(formId))} />;
}
