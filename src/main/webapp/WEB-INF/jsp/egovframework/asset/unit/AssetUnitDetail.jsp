<%--
  개별 자산 상세: 정보, 가능한 상태 변경(반납·수리·수리 완료·폐기), 전체 이력 (자산담당자)
--%>
<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta http-equiv="content-type" content="text/html; charset=utf-8">
<title>개별 자산 상세</title>
<link type="text/css" rel="stylesheet" href="<c:url value='/css/egovframework/com/com.css' />">
</head>
<body>

<div class="wTableFrm">
	<h2>개별 자산 상세</h2>

	<c:if test="${not empty message}">
	<p role="alert" style="margin:0 0 7px; padding:8px 10px; border:1px solid #b1bbcb; background:#fff8e5;"><c:out value="${message}"/></p>
	</c:if>

	<table class="wTable">
	<caption>개별 자산 정보</caption>
	<colgroup><col style="width:20%;"><col></colgroup>
	<tbody>
	<tr>
		<th scope="row">자산번호</th>
		<td class="left"><c:out value="${unit.assetNo}"/></td>
	</tr>
	<tr>
		<th scope="row">품목</th>
		<td class="left"><a href="<c:url value='/asset/item/form.do'><c:param name='itemCode' value='${unit.itemCode}'/></c:url>"><c:out value="${unit.itemNm}"/></a><c:if test="${unit.useAt != 'Y'}"> (사용 중지)</c:if></td>
	</tr>
	<tr>
		<th scope="row">상태</th>
		<td class="left"><strong><c:out value="${sttusNm[unit.sttus]}"/></strong></td>
	</tr>
	<tr>
		<th scope="row">현재 사용자</th>
		<td class="left"><c:if test="${not empty unit.userId}"><c:out value="${unit.userNm}"/> (<c:out value="${unit.userId}"/>)</c:if></td>
	</tr>
	<tr>
		<th scope="row">비고</th>
		<td class="left"><c:out value="${unit.rm}"/></td>
	</tr>
	<tr>
		<th scope="row">등록</th>
		<td class="left"><c:out value="${unit.frstRegisterId}"/> <fmt:formatDate value="${unit.frstRegistPnttm}" pattern="yyyy-MM-dd HH:mm:ss"/></td>
	</tr>
	</tbody>
	</table>
	<div class="btn">
		<span class="btn_s"><a href="<c:url value='/asset/unit/list.do'/>" title="목록으로 이동">목록</a></span>
	</div>

	<h2 style="clear:both; margin-top:20px;">상태 변경</h2>
	<c:choose>
	<c:when test="${unit.sttus == 'IN_USE'}">
	<form action="<c:url value='/asset/unit/status.do'/>" method="post">
	<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
	<input type="hidden" name="assetNo" value="<c:out value='${unit.assetNo}'/>"/>
	<input type="hidden" name="toSttus" value="STORED"/>
	<table class="wTable">
	<caption>반납</caption>
	<colgroup><col style="width:20%;"><col></colgroup>
	<tbody>
	<tr>
		<th scope="row"><label for="returnReason">반납 사유</label></th>
		<td class="left"><input type="text" id="returnReason" name="reason" title="반납 사유 입력(선택)" size="50" maxlength="500"/> 반납하면 보관 상태가 되고 현재 사용자가 비워집니다.</td>
	</tr>
	</tbody>
	</table>
	<div class="btn"><input type="submit" class="s_submit" value="반납" title="반납 버튼"/></div>
	</form>
	<p style="clear:both;">사용 중인 자산은 수리·폐기할 수 없습니다. 먼저 반납하세요.</p>
	</c:when>

	<c:when test="${unit.sttus == 'STORED' && not canTakeOut}">
	<p>승인으로 확보된 자산이라 지금은 수리·폐기할 수 없습니다. (보관 <c:out value="${item.storedQty}"/>, 확보 <c:out value="${item.reservedQty}"/>) 지급은 신청서 지급 화면에서 합니다.</p>
	</c:when>

	<c:when test="${unit.sttus == 'STORED'}">
	<p>보관 자산을 수리·폐기로 빼면 보관 수가 줄어듭니다(보관 <c:out value="${item.storedQty}"/>, 확보 <c:out value="${item.reservedQty}"/>). 지급은 신청서 지급 화면에서 합니다.</p>
	<form action="<c:url value='/asset/unit/status.do'/>" method="post">
	<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
	<input type="hidden" name="assetNo" value="<c:out value='${unit.assetNo}'/>"/>
	<input type="hidden" name="toSttus" value="REPAIR"/>
	<table class="wTable">
	<caption>수리</caption>
	<colgroup><col style="width:20%;"><col></colgroup>
	<tbody>
	<tr>
		<th scope="row"><label for="repairReason">수리 사유</label></th>
		<td class="left"><input type="text" id="repairReason" name="reason" title="수리 사유 입력(선택)" size="50" maxlength="500"/></td>
	</tr>
	</tbody>
	</table>
	<div class="btn"><input type="submit" class="s_submit" value="수리" title="수리 버튼"/></div>
	</form>
	</c:when>

	<c:when test="${unit.sttus == 'REPAIR'}">
	<form action="<c:url value='/asset/unit/status.do'/>" method="post">
	<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
	<input type="hidden" name="assetNo" value="<c:out value='${unit.assetNo}'/>"/>
	<input type="hidden" name="toSttus" value="STORED"/>
	<table class="wTable">
	<caption>수리 완료</caption>
	<colgroup><col style="width:20%;"><col></colgroup>
	<tbody>
	<tr>
		<th scope="row"><label for="repairDoneReason">수리 결과</label></th>
		<td class="left"><input type="text" id="repairDoneReason" name="reason" title="수리 결과 입력(선택)" size="50" maxlength="500"/> 수리 완료하면 보관 상태가 됩니다.</td>
	</tr>
	</tbody>
	</table>
	<div class="btn"><input type="submit" class="s_submit" value="수리 완료" title="수리 완료 버튼"/></div>
	</form>
	</c:when>

	<c:otherwise>
	<p>폐기된 자산입니다. 폐기는 끝 상태라 더 바꿀 수 없고 기록은 그대로 남습니다.</p>
	</c:otherwise>
	</c:choose>

	<c:if test="${(unit.sttus == 'STORED' && canTakeOut) || unit.sttus == 'REPAIR'}">
	<form action="<c:url value='/asset/unit/status.do'/>" method="post" onsubmit="return confirm('폐기하시겠습니까? 폐기는 되돌릴 수 없습니다.');">
	<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
	<input type="hidden" name="assetNo" value="<c:out value='${unit.assetNo}'/>"/>
	<input type="hidden" name="toSttus" value="DISPOSED"/>
	<table class="wTable">
	<caption>폐기</caption>
	<colgroup><col style="width:20%;"><col></colgroup>
	<tbody>
	<tr>
		<th scope="row"><label for="disposeReason">폐기 사유 <span class="pilsu">*</span></label></th>
		<td class="left"><input type="text" id="disposeReason" name="reason" title="폐기 사유 입력(필수)" size="50" maxlength="500" required="required"/> 분실·파손도 폐기로 처리하고 사유를 적습니다.</td>
	</tr>
	</tbody>
	</table>
	<div class="btn"><input type="submit" class="s_submit" value="폐기" title="폐기 버튼"/></div>
	</form>
	</c:if>

	<h2 style="clear:both; margin-top:20px;">자산 이력</h2>
	<table class="board_list">
	<caption>자산 이력</caption>
	<colgroup>
		<col style="width:16%;"><col style="width:9%;"><col style="width:9%;"><col><col style="width:17%;"><col style="width:14%;">
	</colgroup>
	<thead>
	<tr>
		<th scope="col">처리 시각</th>
		<th scope="col">이전 상태</th>
		<th scope="col">이후 상태</th>
		<th scope="col">사유</th>
		<th scope="col">관련 신청서</th>
		<th scope="col">처리자</th>
	</tr>
	</thead>
	<tbody>
	<c:if test="${fn:length(histList) == 0}">
	<tr><td colspan="6">이력이 없습니다.</td></tr>
	</c:if>
	<c:forEach items="${histList}" var="h">
	<tr>
		<td><fmt:formatDate value="${h.processPnttm}" pattern="yyyy-MM-dd HH:mm:ss"/></td>
		<td><c:choose><c:when test="${empty h.beforeSttus}">-</c:when><c:otherwise><c:out value="${sttusNm[h.beforeSttus]}"/></c:otherwise></c:choose></td>
		<td><c:out value="${sttusNm[h.afterSttus]}"/></td>
		<td class="left"><c:out value="${h.resn}"/></td>
		<td><c:out value="${h.reqNo}"/></td>
		<td><c:out value="${h.processNm}"/> (<c:out value="${h.processId}"/>)</td>
	</tr>
	</c:forEach>
	</tbody>
	</table>
</div>

</body>
</html>
