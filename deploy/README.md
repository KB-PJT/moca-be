# EC2 배포 준비

GitHub Actions는 Docker Hub에 `chalie96/moca-be` 이미지를 올린 뒤 AWS Systems Manager(SSM)로
EC2의 Docker Compose를 재기동한다. EC2에는 애플리케이션 저장소를 clone하지 않는다.
배포 시 워크플로가 해당 커밋의 Compose 파일을 내려받고 Docker Hub 이미지를 실행한다.
GitHub Actions를 위해 SSH 22번 포트를 외부에 열 필요가 없다.

## EC2 최초 1회 준비

Amazon Linux 2023에서 Docker와 Docker Compose 플러그인을 준비한 뒤 다음을 실행한다.

```bash
mkdir -p /home/ec2-user/moca
touch /home/ec2-user/moca/.env
chmod 600 /home/ec2-user/moca/.env
```

`/home/ec2-user/moca/.env`에는 실제 RDS, ElastiCache, Google OAuth, 암호화 키 값을 입력한다.
Docker Hub 저장소가 비공개라면 `ec2-user`로 `docker login`을 한 번 실행한다.

## SSM 권한

EC2 인스턴스 프로파일에 `AmazonSSMManagedInstanceCore` 권한을 연결한다. Amazon Linux 2023의
SSM Agent가 중지되어 있다면 아래 명령으로 시작한다.

```bash
sudo systemctl enable --now amazon-ssm-agent
```

GitHub Actions용 IAM 역할은 GitHub OIDC로 연결하고 `ssm:SendCommand`,
`ssm:GetCommandInvocation`, `ssm:ListCommandInvocations` 권한을 해당 EC2 인스턴스와
`AWS-RunShellScript` 문서에만 부여한다.

## GitHub Actions Secrets

| 이름 | 설명 |
| --- | --- |
| `DOCKERHUB_USERNAME` | `chalie96` |
| `DOCKERHUB_TOKEN` | Docker Hub Access Token |
| `AWS_DEPLOY_ROLE_ARN` | GitHub OIDC가 Assume할 IAM 역할 ARN |
| `AWS_REGION` | `ap-northeast-2` |
| `EC2_INSTANCE_ID` | MOCA EC2 인스턴스 ID |

`main` 브랜치에 병합되면 `.github/workflows/deploy.yml`이 WAR 검증, 이미지 빌드·푸시,
SSM 배포와 `/api/v1/health` 상태 확인을 순서대로 수행한다.
