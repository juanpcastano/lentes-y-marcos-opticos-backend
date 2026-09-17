# Bucket de imágenes

El backend escribe imágenes usando las credenciales por defecto del AWS SDK. Las variantes usan `variants/{variantId}/{uuid}.{ext}` y las imágenes de catálogo usan `categories/{uuid}.{ext}` o `brands/{uuid}.{ext}`. El hero de la página principal usa `hero/{uuid}.{ext}`. El bucket no permite escritura pública; solo esos prefijos son públicos para que la tienda pueda mostrar las imágenes mediante URL directa.

## Crear el bucket

Requisitos:

- AWS CLI configurado con permisos de CloudFormation y S3.
- Nombre globalmente único para el bucket.
- Región elegida para el despliegue del backend, por ejemplo `us-east-1`.

```bash
aws cloudformation deploy \
  --stack-name lmopticos-product-images \
  --template-file infra/s3/product-images.yaml \
  --parameter-overrides BucketName=lentesymarcosopticos-images-bucket \
  --region us-east-2
```

## Permisos del rol del backend

Adjuntar al rol IAM que ejecuta Spring Boot una política con alcance únicamente al bucket creado:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:ListBucket"],
      "Resource": "arn:aws:s3:::lentesymarcosopticos-images-bucket",
      "Condition": {
        "StringLike": {
          "s3:prefix": ["variants/*", "categories/*", "brands/*", "hero/*"]
        }
      }
    },
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:DeleteObject"],
      "Resource": [
        "arn:aws:s3:::lentesymarcosopticos-images-bucket/variants/*",
        "arn:aws:s3:::lentesymarcosopticos-images-bucket/categories/*",
        "arn:aws:s3:::lentesymarcosopticos-images-bucket/brands/*",
        "arn:aws:s3:::lentesymarcosopticos-images-bucket/hero/*"
      ]
    }
  ]
}
```

No se deben configurar `AWS_ACCESS_KEY_ID` ni `AWS_SECRET_ACCESS_KEY` en el servidor si se usa un rol IAM de EC2/ECS. En local, el SDK toma el perfil configurado por AWS CLI.

El backend usa `s3:ListBucket` para mostrar en el panel las imágenes existentes de `variants/` (galería y selector de imágenes existentes del formulario de variante), `categories/`, `brands/` y `hero/`. La subida y eliminación requieren permisos sobre objetos del bucket.

## Variables del backend

```bash
AWS_S3_BUCKET=lentesymarcosopticos-images-bucket
AWS_S3_REGION=us-east-2
AWS_S3_PUBLIC_URL_BASE=https://lentesymarcosopticos-images-REEMPLAZAR.s3.us-east-2.amazonaws.com
```

`AWS_S3_PUBLIC_URL_BASE` es opcional; si se omite, el backend construye la URL regional automáticamente. No subir archivos de credenciales al repositorio.
