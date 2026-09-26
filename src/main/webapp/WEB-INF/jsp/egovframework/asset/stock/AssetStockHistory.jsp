<%--
  재고 변동 이력 (자산담당자)
--%>
<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" uri="http://egovframework.gov/ctl/ui"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta http-equiv="content-type" content="text/html; charset=utf-8">
<title>재고 변동 이력</title>
<link type="text/css" rel="stylesheet" href="<c:url value='/css/egovframework/com/com.css' />">
<script type="text/javascript">
function fn_egov_select_linkPage(pageNo) {
	document.histForm.pageIndex.value = pageNo;
	document.histForm.submit();
}
function fn_search() {
	document.histForm.pageIndex.value = 1;
	document.histForm.submit();
}
</script>
</head>
<body>

<form name="histForm" action="<c:url value='/asset/stock/history.do'/>" method="post" onsubmit="fn_search(); return false;">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
<div class="board">
	<h1>재고 변동 이력</h1>

	<div class="search_box" title="검색 조건">
		<ul>
			<li>
				<label for="itemCode">품목</label>
				<select id="itemCode" name="itemCode" title="품목 선택">
					<option value="">전체</option>
					<c:forEach items="${itemList}" var="it">
					<option value="<c:out value='${it.itemCode}'/>" <c:if test="${searchVO.itemCode == it.itemCode}">selected="selected"</c:if>><c:out value="${it.itemNm}"/><c:if test="${it.useAt != 'Y'}"> (중지)</c:if></option>
					</c:forEach>
				</select>
				<input type="submit" class="s_btn" value="조회" title="조회 버튼" />
			</li>
		</ul>
	</div>

	<p>수량 비품은 재고 수량 기준, 개별 자산은 가용(보관 − 확보) 기준으로 증감과 변동 후 값을 기록합니다.</p>

	<table class="board_list">
	<caption>재고 변동 이력 목록</caption>
	<colgroup>
		<col style="width:14%;"><col><col style="width:9%;"><col style="width:7%;"><col style="width:7%;">
		<col style="width:20%;"><col style="width:15%;"><col style="width:8%;">
	</colgroup>
	<thead>
	<tr>
		<th scope="col">처리 시각</th>
		<th scope="col">품목</th>
		<th scope="col">유형</th>
		<th scope="col">증감</th>
		<th scope="col">변동 후</th>
		<th scope="col">사유</th>
		<th scope="col">관련 신청서</th>
		<th scope="col">처리자</th>
	</tr>
	</thead>
	<tbody class="ov">
	<c:if test="${fn:length(resultList) == 0}">
	<tr><td colspan="8">조회된 이력이 없습니다.</td></tr>
	</c:if>
	<c:forEach items="${resultList}" var="row">
	<tr>
		<td><fmt:formatDate value="${row.processPnttm}" pattern="yyyy-MM-dd HH:mm:ss"/></td>
		<td class="left"><c:out value="${row.itemNm}"/></td>
		<td><c:choose>
			<c:when test="${row.histTy == 'IN'}">입고</c:when>
			<c:when test="${row.histTy == 'ADJUST'}">조정</c:when>
			<c:when test="${row.histTy == 'RESERVE'}">승인 확보</c:when>
			<c:when test="${row.histTy == 'RELEASE'}">지급 취소 복구</c:when>
			<c:when test="${row.histTy == 'DELIVER'}">지급</c:when>
			<c:otherwise><c:out value="${row.histTy}"/></c:otherwise>
		</c:choose></td>
		<td><c:if test="${row.changeQty > 0}">+</c:if><c:out value="${row.changeQty}"/></td>
		<td><c:out value="${row.afterQty}"/></td>
		<td class="left"><c:out value="${row.resn}"/></td>
		<td><c:out value="${row.reqNo}"/></td>
		<td><c:out value="${row.processId}"/></td>
	</tr>
	</c:forEach>
	</tbody>
	</table>

	<div class="pagination">
		<ul>
		<ui:pagination paginationInfo="${paginationInfo}" type="image" jsFunction="fn_egov_select_linkPage"/>
		</ul>
	</div>
</div>
<input name="pageIndex" type="hidden" value="<c:out value='${searchVO.pageIndex}'/>">
</form>

</body>
</html>
