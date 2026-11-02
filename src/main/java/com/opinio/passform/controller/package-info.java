/**
 * controller 계층: 요청 DTO를 Bean Validation({@code @Valid})으로 형식 검증한 뒤 Command로 변환해 service를 호출한다.
 * 엔티티를 요청 바인딩이나 응답에 직접 쓰지 않는다. 도메인 예외의 HTTP 상태 매핑은 advice에서만 한다.
 */
package com.opinio.passform.controller;
