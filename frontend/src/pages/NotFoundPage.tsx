import { useNavigate } from 'react-router-dom';
import { Button, Heading } from '../components/UI';
import { paths } from '../routes/paths';

export function NotFoundPage() {
  const navigate = useNavigate();
  return (
    <div className="page-shell">
      <Heading
        eyebrow="Passform"
        title="페이지를 찾을 수 없어요"
        desc="주소를 다시 확인해 주세요."
        action={<Button onClick={() => navigate(paths.home)}>처음으로</Button>}
      />
    </div>
  );
}
