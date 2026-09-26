<%--
  재고 부족 집계 결과 (자산담당자)
--%>
<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" uri="http://egovframework.gov/ctl/ui"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta http-equiv="content-type" content="text/html; charset=utf-8">
<title>재고 부족 집계</title>
<link type="text/css" rel="stylesheet" href="<c:url value='/css/egovframework/com/com.css' />">
<script type="text/javascript">
function fn_egov_select_linkPage(pageNo) {
	document.shortageListForm.pageIndex.value = pageNo;
	document.shortageListForm.submit();
}
function fn_run() {
	return confirm("지금 재고 부족 집계를 실행하시겠습니까?");
}
</script>
</head>
<body>

<div class="board">
	<h1>재고 부족 집계</h1>

	<c:if test="${not empty message}">
	<p role="alert" style="margin:0 0 7px; padding:8px 10px; border:1px solid #b1bbcb; background:#fff8e5;"><c:out value="${message}"/></p>
	</c:if>

	<div class="search_box" title="실행">
		<ul>
			<li>
				<form name="shortageRunForm" action="<c:url value='/asset/shortage/run.do'/>" method="post" onsubmit="return fn_run();">
				<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
				<label for="runNow">매일 밤 자동으로 실행됩니다(기본 02:00). 바로 집계하려면</label>
				<input type="submit" id="runNow" class="s_btn" value="지금 실행" title="재고 부족 집계 지금 실행" />
				</form>
			</li>
			<li>
				<span class="btn_b"><a href="<c:url value='/sym/bat/getBatchResultList.do'/>" title="배치 결과 화면으로 이동(실행 성공·실패 기록)">배치 결과</a></span>
			</li>
		</ul>
	</div>

	<table class="wTable">
	<caption>최근 실행</caption>
	<colgroup><col style="width:20%;"><col style="width:30%;"><col style="width:20%;"><col style="width:30%;"></colgroup>
	<tbody>
	<c:choose>
	<c:when test="${empty latestRun}">
	<tr><td colspan="4">아직 실행된 집계가 없습니다.</td></tr>
	</c:when>
	<c:otherwise>
	<tr>
		<th scope="row">실행 시각</th>
		<td class="left"><c:out value="${latestRun.runPnttm}"/></td>
		<th scope="row">실행 구분</th>
		<td class="left"><c:choose><c:when test="${latestRun.runTy == 'MANUAL'}">즉시 실행</c:when><c:otherwise>자동(야간)</c:otherwise></c:choose></td>
	</tr>
	<tr>
		<th scope="row">실행자</th>
		<td class="left"><c:choose><c:when test="${empty latestRun.runUserId}">시스템</c:when><c:otherwise><c:out value="${latestRun.runUserNm}"/> (<c:out value="${latestRun.runUserId}"/>)</c:otherwise></c:choose></td>
		<th scope="row">부족 품목</th>
		<td class="left"><c:out value="${latestRun.itemCnt}"/>건</td>
	</tr>
	</c:otherwise>
	</c:choose>
	</tbody>
	</table>

	<form name="shortageListForm" action="<c:url value='/asset/shortage/result.do'/>" method="post">
	<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
	<table class="board_list">
	<caption>부족 품목 목록</caption>
	<colgroup>
		<col style="width:7%;"><col style="width:18%;"><col><col style="width:10%;"><col style="width:10%;"><col style="width:10%;"><col style="width:10%;">
	</colgroup>
	<thead>
	<tr>
		<th scope="col">번호</th>
		<th scope="col">품목코드</th>
		<th scope="col">품목명</th>
		<th scope="col">유형</th>
		<th scope="col">가용</th>
		<th scope="col">최소 수량</th>
		<th scope="col">부족분</th>
	</tr>
	</thead>
	<tbody class="ov">
	<c:if test="${fn:length(resultList) == 0}">
	<tr><td colspan="7">부족 품목이 없습니다.</td></tr>
	</c:if>
	<c:forEach items="${resultList}" var="row" varStatus="status">
	<tr>
		<td><c:out value="${(searchVO.pageIndex - 1) * searchVO.pageUnit + status.count}"/></td>
		<td><c:out value="${row.itemCode}"/></td>
		<td class="left"><c:out value="${row.itemNm}"/></td>
		<td><c:choose><c:when test="${row.itemTy == 'UNIT'}">개별 자산</c:when><c:otherwise>수량 비품</c:otherwise></c:choose></td>
		<td><c:out value="${row.availQty}"/></td>
		<td><c:out value="${row.minQty}"/></td>
		<td><strong><c:out value="${row.shortQty}"/></strong></td>
	</tr>
	</c:forEach>
	</tbody>
	</table>

	<div class="pagination">
		<ul>
		<ui:pagination paginationInfo="${paginationInfo}" type="image" jsFunction="fn_egov_select_linkPage"/>
		</ul>
	</div>
	<input name="pageIndex" type="hidden" value="<c:out value='${searchVO.pageIndex}'/>">
	</form>
</div>

</body>
</html>
