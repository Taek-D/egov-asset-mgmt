<%--
  개별 자산 등록(입고) (자산담당자)
--%>
<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta http-equiv="content-type" content="text/html; charset=utf-8">
<title>개별 자산 등록</title>
<link type="text/css" rel="stylesheet" href="<c:url value='/css/egovframework/com/com.css' />">
</head>
<body>

<div class="wTableFrm">
	<h2>개별 자산 등록</h2>

	<c:if test="${not empty message}">
	<p role="alert" style="margin:0 0 7px; padding:8px 10px; border:1px solid #b1bbcb; background:#fff8e5;"><c:out value="${message}"/></p>
	</c:if>

	<p>등록은 입고입니다. 한 대씩 자산번호로 등록하면 보관 상태가 되고 재고 변동 이력에 입고(+1)가 남습니다.</p>

	<form name="unitForm" action="<c:url value='/asset/unit/register.do'/>" method="post">
	<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
	<table class="wTable">
	<caption>개별 자산 등록</caption>
	<colgroup><col style="width:20%;"><col></colgroup>
	<tbody>
	<tr>
		<th scope="row"><label for="assetNo">자산번호 <span class="pilsu">*</span></label></th>
		<td class="left"><input type="text" id="assetNo" name="assetNo" title="자산번호 입력(폐기 자산 포함 중복 불가)" size="30" maxlength="50" required="required" value="<c:out value='${unit.assetNo}'/>"/>
			폐기된 자산을 포함해 이미 쓰인 번호는 다시 쓸 수 없습니다.</td>
	</tr>
	<tr>
		<th scope="row"><label for="itemCode">품목 <span class="pilsu">*</span></label></th>
		<td class="left">
			<select id="itemCode" name="itemCode" title="품목 선택(사용 중인 개별 자산 품목)" required="required">
				<option value="">선택</option>
				<c:forEach items="${itemList}" var="it">
				<option value="<c:out value='${it.itemCode}'/>" <c:if test="${unit.itemCode == it.itemCode}">selected="selected"</c:if>><c:out value="${it.itemNm}"/></option>
				</c:forEach>
			</select>
			<c:if test="${fn:length(itemList) == 0}"><span>사용 중인 개별 자산 품목이 없습니다. 품목 관리에서 먼저 등록하세요.</span></c:if>
		</td>
	</tr>
	<tr>
		<th scope="row"><label for="rm">비고</label></th>
		<td class="left"><input type="text" id="rm" name="rm" title="비고 입력" size="60" maxlength="500" value="<c:out value='${unit.rm}'/>"/></td>
	</tr>
	</tbody>
	</table>
	<div class="btn">
		<input type="submit" class="s_submit" value="등록" title="자산 등록 버튼"/>
		<span class="btn_s"><a href="<c:url value='/asset/unit/list.do'/>" title="목록으로 이동">목록</a></span>
	</div>
	</form>
</div>

</body>
</html>
